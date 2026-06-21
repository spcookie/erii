package uesugi.core.state.memory

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.params.LLMParams
import kotlinx.coroutines.*
import kotlinx.datetime.format
import kotlinx.serialization.Serializable
import uesugi.common.LLMProviderChoice
import uesugi.common.data.HistoryRecord
import uesugi.common.toolkit.DateTimeFormat
import uesugi.common.toolkit.logger
import uesugi.common.toolkit.ref
import kotlin.time.ExperimentalTime

/**
 * 记忆代理 - 负责从历史消息中提取和生成各类记忆数据
 *
 * 记忆整理流程（4步）：
 * 1. LLM 提取事实（extractFacts）
 *    → 输出 {"facts": [{keyword, description, values, subjects, scope}]}
 * 2. LLM 冲突解决（resolveConflicts）
 *    → 新事实 vs 已有事实 → 决策：ADD / UPDATE / DELETE / NONE
 * 3. 批量执行决策（executeDecisions）
 *    → 数据库操作（增删改）
 * 4. 统一向量同步（syncVectors）
 *    → 生成 Embedding → 写入 Vector Store
 */
class MemoryAgent(
    val memoryRepository: MemoryRepository,
    val factVectorStore: FactVectorStore,
    val promptExecutor: PromptExecutor
) {

    companion object {
        private val log = logger()
    }

    // ==================== 数据模型 ====================

    /**
     * 用户画像分析结果
     */
    @Serializable
    @LLMDescription("用户画像分析结果")
    data class UserProfileAnalysis(
        @property:LLMDescription("用户ID")
        val userId: String,
        @property:LLMDescription("用户性格、行为特征")
        val profile: String,
        @property:LLMDescription("兴趣偏好")
        val preferences: String
    )

    /**
     * 批量执行影响的事实集合（内部使用）
     */
    private data class AffectedFacts(
        val added: List<FactsRecord> = emptyList(),
        val updated: List<FactsRecord> = emptyList(),
        val deleted: List<DeletedFact> = emptyList()
    )

    private data class DeletedFact(val id: Int, val vectorId: String?)

    // ==================== 用户画像分析 ====================

    /**
     * 分析用户画像和偏好
     */
    @OptIn(ExperimentalTime::class)
    suspend fun analyzeUserProfile(
        messages: List<HistoryRecord>,
        userProfileEntity: UserProfileRecord?
    ): UserProfileAnalysis {
        log.debug("Start analyzing user profile, userId=${messages.firstOrNull()?.userId}, message count=${messages.size}")

        val prompt = prompt("__memory_user_profile__", LLMParams(maxTokens = 65536)) {
            system(
                """
                你是一名【用户行为分析专家】，擅长从群聊历史消息中提取可验证的行为特征。

                请基于给定的【用户在群聊中的历史消息文本】，分析并输出该用户的画像信息。

                ====================
                【分析对象】
                - 单一用户
                - 数据来源：群聊消息（可能存在噪声、复读、引用他人内容）

                ====================
                【分析原则（必须遵守）】

                1. 只基于**实际出现的消息内容**进行分析
                   - 不得进行心理动机、人格类型（如内向/外向）或现实身份的推断
                   - 不得使用“可能是、像是、应该是”等猜测性表述

                2. 结论必须可由消息行为直接支持
                   - 优先使用：发言长度、话题连续性、提问/输出比例、技术名词使用情况等行为证据
                   - 群聊噪声（表情、复读、无新增信息的引用）应降低权重或忽略

                3. 当某一维度信息不足以支撑结论时：
                   - 该字段输出空字符串 ""

                4. 使用**简洁、客观、中性**的语言
                   - 每一项控制在 50 字左右
                   - 不输出示例、不输出分析过程

                【需要输出的字段】

                请分析并输出以下 2 个字段：

                1. profile（用户画像）
                   - 关注其在群聊中的**行为模式与角色特征**
                   - 如：是否经常输出完整观点、是否承担技术解释/总结角色、参与频率是否稳定

                2. preferences（兴趣偏好）
                   - 基于其**主动参与和反复出现的话题**
                   - 可体现技术领域、讨论深度、偏向问题类型（设计 / 实现 / 排错）

                【重要】请仅输出**一个**JSON对象，不包含任何其他文字、解释、注释或标记。
                """.trimIndent()
            )

            val msg = messages.joinToString("\n") { it.asLlmPrompt() }
            user {
                text(
                    """
                    分析以下用户的群聊行为特征和兴趣偏好。请基于消息内容提取可验证的行为特征，输出结构化JSON。

                    数据如下：
                    """.trimIndent()
                )
                text(buildString {
                    append("用户ID: ${messages.firstOrNull()?.userId ?: "unknown"}")
                    if (userProfileEntity != null) {
                        append("参考：")
                        append("之前分析的用户画像信息: ${userProfileEntity.profile}")
                        append("之前分析的用户偏好信息: ${userProfileEntity.preferences}")
                    }
                    append("历史消息：")
                    append(msg)
                    append("请分析该用户的画像和偏好。")
                    append("只输出该用户当前最新的画像和偏好。")
                })
            }
        }

        val promptExecutor by ref<PromptExecutor>()

        val result = promptExecutor.executeStructured<UserProfileAnalysis>(
            prompt = prompt,
            model = LLMProviderChoice.Pro,
            fixingParser = StructureFixingParser(
                model = LLMProviderChoice.Lite,
                retries = 2
            )
        )

        return result.getOrThrow().data.also {
            log.debug("User profile analysis completed, userId=${it.userId}")
        }
    }

    // ==================== 记忆整理核心流程 ====================

    /**
     * 整理群记忆 - 新流程
     *
     * Step 1: LLM 提取事实 → FactExtractionResult
     * Step 2: LLM 冲突解决 → ConflictResolutionResult (ADD/UPDATE/DELETE/NONE)
     * Step 3: 批量执行决策 → 数据库操作
     * Step 4: 统一向量同步 → Embedding → Vector Store
     */
    @OptIn(ExperimentalTime::class)
    suspend fun organize(
        botMark: String,
        groupId: String,
        messages: List<HistoryRecord>
    ) {
        if (messages.isEmpty()) return

        // Step 1: 提取事实
        val extraction = try {
            extractFacts(messages)
        } catch (e: Exception) {
            log.error("Fact extraction failed, groupId=$groupId", e)
            return
        }
        if (extraction.facts.isEmpty()) {
            log.debug("No facts extracted, groupId=$groupId")
            return
        }
        log.info("Fact extraction completed, groupId=$groupId, extracted ${extraction.facts.size} facts")

        // Step 2: 获取已有事实
        val existingFacts = withContext(Dispatchers.IO) {
            memoryRepository.getValidFacts(botMark, groupId)
        }

        // Step 3: 冲突解决
        val resolution = if (existingFacts.isEmpty()) {
            ConflictResolutionResult(
                decisions = extraction.facts.map {
                    MemoryDecision(
                        action = MemoryAction.ADD,
                        newFact = it,
                        reason = "New fact, no existing memory"
                    )
                }
            )
        } else {
            try {
                resolveConflicts(extraction.facts, existingFacts)
            } catch (e: Exception) {
                log.error("Conflict resolution failed, groupId=$groupId", e)
                return
            }
        }

        val actionCounts = resolution.decisions.groupingBy { it.action }.eachCount()
        log.info("Conflict resolution completed, groupId=$groupId, decisions=$actionCounts")

        // Step 4: 批量执行决策
        val affectedFacts = executeDecisions(botMark, groupId, resolution.decisions)

        // Step 5: 统一向量同步
        syncVectors(botMark, groupId, affectedFacts)

        log.info(
            "Memory organization completed, groupId=$groupId, " +
                    "added=${affectedFacts.added.size}, " +
                    "updated=${affectedFacts.updated.size}, " +
                    "deleted=${affectedFacts.deleted.size}"
        )
    }

    // ==================== Step 1: 事实提取 ====================

    @OptIn(ExperimentalTime::class)
    private suspend fun extractFacts(messages: List<HistoryRecord>): FactExtractionResult {
        val prompt = prompt("__memory_fact_extract__", LLMParams(maxTokens = 65536)) {
            system(
                """
                你是一名事实提取专家。从给定的群聊消息中提取有价值、长久有效的事实信息。

                ## 提取标准
                1. 只提取事实性信息，不提取观点、情绪、临时信息
                2. 关注：
                   - 个人信息：职业、居住地、技能、学历、年龄等
                   - 群组共识：共同决定、约定、规则等
                   - 重要经历：旅行、项目、获奖等
                   - 持久偏好：喜欢的技术、游戏、音乐、食物等
                3. 忽略：
                   - 日常寒暄、问候
                   - 临时讨论、临时安排
                   - 情绪波动、吐槽
                   - 无信息量的内容（如纯表情、复读）
                   - 不确定的信息（包含"可能"、"好像"、"听说"、"也许"等词）

                ## 输出字段说明
                - keyword: 关键词，2-6个字，概括事实类别
                - description: 事实描述，简洁准确，20-50字
                - values: 相关值/属性，如地点名称、职业名称、具体数值等
                - subjects: 涉及的用户ID，逗号分隔。如果涉及多个用户都要列出
                - scope: 范围类型
                  - "user": 个人属性，只与特定用户相关
                  - "group": 群组共识，与整个群组相关

                ## 输出格式
                只输出一个 JSON 对象，包含 facts 数组。没有提取到事实时返回 {"facts": []}。
                不要输出任何其他文字、解释或标记。
                """.trimIndent()
            )

            system(
                """
                # 过时记忆识别补充规则
                如果消息明确表达“不是了、已经不、改成、换成、搬到、离开、辞职、分手、结婚、毕业、取消、废弃”等状态变化，
                也要提取成事实候选。此类候选用于后续冲突解决，不要因为它是否定句就忽略。

                对于状态变化，优先提取“当前新状态”的事实；如果只有旧状态被否定但没有新状态，也要提取能表达旧事实失效的候选，
                让冲突解决阶段有机会把已有记忆标记为 DELETE。
                """.trimIndent()
            )

            val msgText = messages.joinToString("\n") { it.asLlmPrompt() }

            user {
                text(
                    """
                    请从以下群聊消息中提取事实：

                    """.trimIndent()
                )
                text(
                    """
                    $msgText

                    请输出 JSON 格式的事实列表。
                    """.trimIndent()
                )
            }
        }

        val result = promptExecutor.executeStructured<FactExtractionResult>(
            prompt = prompt,
            model = LLMProviderChoice.Pro,
            fixingParser = StructureFixingParser(
                model = LLMProviderChoice.Lite,
                retries = 2
            )
        )

        return result.getOrThrow().data
    }

    // ==================== Step 2: 冲突解决 ====================

    @OptIn(ExperimentalTime::class)
    private suspend fun resolveConflicts(
        newFacts: List<ExtractedFact>,
        existingFacts: List<FactsRecord>
    ): ConflictResolutionResult {
        val prompt = prompt("__memory_conflict_resolve__", LLMParams(maxTokens = 65536)) {
            system(
                """
                你是一名记忆冲突解决专家。你的任务是将新提取的事实与已有记忆进行对比，做出最优决策。

                ## 决策类型
                - ADD: 新事实与所有已有记忆都不冲突，应该添加为新记忆
                - UPDATE: 新事实与某条已有记忆描述同一属性但值不同/更完整，应该更新该记忆
                - DELETE: 某条已有记忆已被证明过时或不准确，应该废弃（注意：DELETE 仅标记旧事实废弃，同时你需要 ADD 新版本）
                - NONE: 新事实与已有记忆完全重复，无需操作

                ## 决策规则
                1. **同一属性不同值** → UPDATE
                   例：已有"用户A住在北京"，新事实"用户A搬到上海" → UPDATE（将旧事实标记为DELETE，新事实标记为ADD）
                2. **补充或修正** → UPDATE
                   例：已有"用户A是程序员"，新事实"用户A是前端工程师" → UPDATE
                3. **完全独立的新信息** → ADD
                4. **已被明确否定** → DELETE（旧事实）+ ADD（新事实）
                   例：已有"用户A单身"，消息中明确说"我已经结婚了" → DELETE 旧事实，ADD 新事实
                5. **完全重复** → NONE
                6. **事实已被更具体的版本覆盖** → UPDATE

                ## 注意事项
                - 每个新事实必须对应一个决策
                - UPDATE 时必须指定 existingFactId（要更新的已有事实ID）
                - DELETE 时必须指定 existingFactId（要废弃的已有事实ID）
                - 状态变化时（如单身→已婚），应该产生两个决策：DELETE 旧 + ADD 新
                - 不要过度合并，不同属性的事实应该独立处理
                - 当信息不足以判断时，优先选择 ADD 而不是 UPDATE

                ## 输出格式
                只输出一个 JSON 对象，包含 decisions 数组。不要输出任何其他文字。
                """.trimIndent()
            )

            system(
                """
                # DELETE / UPDATE 优先级补充
                你的目标不是只追加新记忆，而是维护一组当前仍然真实的记忆。
                当新事实与已有事实描述同一主体的同一属性，但值、状态、地点、关系、职业、偏好、计划已经变化时：
                - 必须优先选择 UPDATE，并填写 existingFactId 与 newFact。
                - 如果新消息只是否定旧事实、但没有给出新值，选择 DELETE，并填写 existingFactId。
                - 不要把“用户不住北京了”这类内容作为一条长期负面新事实直接 ADD；应让旧的“住北京”失效。
                - 对同一主体同一属性，避免同时保留旧值和新值。
                """.trimIndent()
            )

            val newFactsText = newFacts.joinToString("\n") {
                "- keyword: ${it.keyword}, description: ${it.description}, values: ${it.values}, subjects: ${it.subjects}, scope: ${it.scope}"
            }

            val existingFactsText = existingFacts.joinToString("\n") {
                "ID: ${it.id} | keyword: ${it.keyword} | description: ${it.description} | values: ${it.values} | subjects: ${it.subjects} | scope: ${it.scopeType}"
            }

            user {
                text(
                    """
                    ## 新提取的事实
                    """.trimIndent()
                )
                text(
                    """
                    $newFactsText

                    ## 已有记忆
                    $existingFactsText

                    请对比并输出决策列表。
                    """.trimIndent()
                )
            }
        }

        val result = promptExecutor.executeStructured<ConflictResolutionResult>(
            prompt = prompt,
            model = LLMProviderChoice.Pro,  // 冲突解决需要更强的模型
            fixingParser = StructureFixingParser(
                model = LLMProviderChoice.Lite,
                retries = 2
            )
        )

        return result.getOrThrow().data
    }

    // ==================== Step 3: 批量执行决策 ====================

    private suspend fun executeDecisions(
        botMark: String,
        groupId: String,
        decisions: List<MemoryDecision>
    ): AffectedFacts {
        val added = mutableListOf<FactsRecord>()
        val updated = mutableListOf<FactsRecord>()
        val deleted = mutableListOf<DeletedFact>()

        for (decision in decisions) {
            when (decision.action) {
                MemoryAction.ADD -> {
                    decision.newFact?.let { fact ->
                        createAndFetchFact(botMark, groupId, fact)?.let { added.add(it) }
                    }
                }

                MemoryAction.UPDATE -> {
                    val existingId = decision.existingFactId ?: continue
                    val newFact = decision.newFact ?: continue

                    val oldVectorId = withContext(Dispatchers.IO) {
                        memoryRepository.getFactById(existingId)?.vectorId
                    }
                    deleted.add(DeletedFact(existingId, oldVectorId))

                    withContext(Dispatchers.IO) {
                        memoryRepository.deprecateFactsById(botMark, groupId, existingId, newFact.scope)
                    }
                    createAndFetchFact(botMark, groupId, newFact)?.let { updated.add(it) }
                }

                MemoryAction.DELETE -> {
                    val existingId = decision.existingFactId ?: continue
                    val fact = withContext(Dispatchers.IO) {
                        memoryRepository.getFactById(existingId)
                    }
                    fact?.let {
                        deleted.add(DeletedFact(existingId, it.vectorId))
                        withContext(Dispatchers.IO) {
                            memoryRepository.deprecateFactsById(botMark, groupId, existingId, it.scopeType)
                        }
                    }
                }

                MemoryAction.NONE -> {}
            }
        }

        return AffectedFacts(added, updated, deleted)
    }

    private suspend fun createAndFetchFact(
        botMark: String,
        groupId: String,
        fact: ExtractedFact
    ): FactsRecord? {
        val id = withContext(Dispatchers.IO) {
            memoryRepository.createFact(
                botMark, groupId,
                fact.keyword, fact.description,
                fact.values, fact.subjects, fact.scope
            )
        }
        return withContext(Dispatchers.IO) {
            memoryRepository.getFactById(id)
        }
    }

    // ==================== Step 4: 统一向量同步 ====================

    private suspend fun syncVectors(
        botMark: String,
        groupId: String,
        affectedFacts: AffectedFacts
    ) {
        for (deleted in affectedFacts.deleted) {
            deleted.vectorId?.let { vectorId ->
                factVectorStore.deleteVector(vectorId, botMark, groupId)
                log.debug("Old vector deleted, factId=${deleted.id}, vectorId=$vectorId")
            }
        }

        val factsToIndex = affectedFacts.added + affectedFacts.updated
        coroutineScope {
            factsToIndex.map { fact ->
                async {
                    val vectorId = factVectorStore.indexFact(fact)
                    withContext(Dispatchers.IO) {
                        memoryRepository.updateFactVectorId(fact.id, vectorId)
                    }
                    log.debug("Vector index created, factId=${fact.id}, vectorId=$vectorId")
                }
            }.awaitAll()
        }
    }
}

internal fun HistoryRecord.asLlmPrompt(): String =
    "[ID:${id ?: 0} $userId ${createdAt.format(DateTimeFormat)}] ${content ?: ""}"
