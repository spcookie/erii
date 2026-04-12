package uesugi.core.state.memory

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.message.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.koin.core.context.GlobalContext
import uesugi.common.HistoryRecord
import uesugi.common.LLMModelsChoice
import uesugi.common.toolkit.DateTimeFormat
import uesugi.common.toolkit.logger
import uesugi.common.toolkit.ref
import kotlin.time.ExperimentalTime

/**
 * 记忆代理 - 负责从历史消息中提取和生成各类记忆数据
 */
class MemoryAgent(
    val memoryRepository: MemoryRepository,
    val factVectorStore: FactVectorStore,
    val promptExecutor: PromptExecutor
) {

    companion object {
        private val log = logger()
    }

    /**
     * 历史消息数据模型
     */
    data class MemoryMessage(
        val id: Int,
        val userId: String,
        val groupId: String,
        val time: LocalDateTime,
        val content: String
    ) {
        fun asLlmPrompt() = "[ID:$id $userId ${time.format(DateTimeFormat)}] $content"
    }

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

    object MemoryScopesSerializer : KSerializer<MemoryScopes> {
        override val descriptor = PrimitiveSerialDescriptor("MemoryScopes", PrimitiveKind.STRING)

        override fun deserialize(decoder: Decoder): MemoryScopes {
            return when (decoder.decodeString().lowercase()) {
                "user" -> MemoryScopes.USER
                "group" -> MemoryScopes.GROUP
                else -> throw IllegalArgumentException("Unknown scope")
            }
        }

        override fun serialize(encoder: Encoder, value: MemoryScopes) {
            encoder.encodeString(value.name.lowercase())
        }
    }

    @Serializable(with = MemoryScopesSerializer::class)
    enum class MemoryScopes {
        @LLMDescription("用户作用域 USER")
        USER,

        @LLMDescription("群组作用域 GROUP")
        GROUP
    }

    /**
     * 摘要总结结果
     */
    @Serializable
    data class SummaryAnalysis(
        val timeRange: String,         // 时间范围
        val content: String,           // 摘要内容
        val keyPoints: List<String>,   // 关键要点
        val emotionalTone: String,     // 情感基调
        val participantIds: List<String>, // 参与者ID
        val messageCount: Int          // 消息数量
    )

    /**
     * 分析用户画像和偏好
     *
     * @param messages 用户的历史消息列表
     * @return 用户画像分析结果
     */
    @OptIn(ExperimentalTime::class)
    suspend fun analyzeUserProfile(
        messages: List<MemoryMessage>,
        userProfileEntity: UserProfileRecord?
    ): UserProfileAnalysis {
        log.debug("开始分析用户画像, userId=${messages.firstOrNull()?.userId}, 消息数=${messages.size}")

        val prompt = prompt("分析用户画像和偏好") {
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
                
                仅输出一个 JSON 对象，不包含任何额外说明或注释
                """.trimIndent()
            )

            val msg = messages.joinToString("\n") { it.asLlmPrompt() }
            user(
                buildString {
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
                }
            )
        }

        val promptExecutor by ref<PromptExecutor>()

        val result = promptExecutor.executeStructured<UserProfileAnalysis>(
            prompt = prompt,
            model = LLMModelsChoice.Flash,
            fixingParser = StructureFixingParser(
                model = LLMModelsChoice.Lite,
                retries = 2
            )
        )

        return result.getOrThrow().data.also {
            log.debug("用户画像分析完成, userId=${it.userId}")
        }
    }

    /**
     * 生成对话摘要
     *
     * @param messages 历史消息列表
     * @param groupId 范围ID(群ID或用户ID)
     * @return 摘要分析结果
     */
    @OptIn(ExperimentalTime::class)
    suspend fun generateSummary(messages: List<MemoryMessage>, groupId: String): SummaryAnalysis {
        log.debug("开始生成对话摘要, groupId=$groupId, 消息数=${messages.size}")

        val prompt = prompt("生成对话摘要") {
            system(
                """
                # Role
                你是一名专业的社群对话分析师，拥有极强的信息归纳与上下文理解能力。
                
                # Workflow
                请按照以下步骤处理输入的群聊记录：
                1. **数据清洗**：剔除无意义的语气词、重复刷屏、系统消息。
                2. **话题聚类**：识别对话中并行发生的多个话题（如有），聚焦于最核心的主题。
                3. **因果分析**：识别对话的触发点（Trigger）和最终结论（Resolution）。
                4. **生成输出**：基于上述分析，生成符合要求的JSON。
                
                # Output Fields Spec
                1. **timeRange**: 精确覆盖首尾消息的时间，格式 "yyyy-MM-dd HH:mm"。
                2. **content**: 
                   - 必须包含：讨论背景、核心冲突/观点、达成的一致或遗留问题。
                   - 风格：简练、商务、非口语化。字数控制在150字左右。
                3. **keyPoints**: 
                   - 提取3-5个"信息胶囊"。
                   - 格式示例："需求变更：确认将登录页改为蓝色主题"。
                   - 避免泛泛而谈（如"讨论了需求"是无效的，要写明"讨论了什么需求"）。
                4. **emotionalTone**: 准确判断整体氛围（如：焦虑、欢快、激烈争论、按部就班）。
                5. **participantIds**: 参与有效发言的用户ID列表（去重）。
                6. **messageCount**: 原始消息总数。
                
                # Output Format
                请仅输出纯JSON字符串，格式如下：
                """.trimIndent()
            )

            val msg = messages.joinToString("\n") { it.asLlmPrompt() }
            val startTime = messages.firstOrNull()?.time?.format(DateTimeFormat) ?: "unknown"
            val endTime = messages.lastOrNull()?.time?.format(DateTimeFormat) ?: "unknown"

            user(
                """
                时间范围: $startTime ~ $endTime
                
                历史消息:
                $msg
                
                请生成对话摘要。
                """.trimIndent()
            )
        }

        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()

        val result = promptExecutor.executeStructured<SummaryAnalysis>(
            prompt = prompt,
            model = LLMModelsChoice.Flash,
            fixingParser = StructureFixingParser(
                model = LLMModelsChoice.Lite,
                retries = 2
            )
        )

        return result.getOrThrow().data.also {
            log.debug("对话摘要生成完成, groupId=$groupId")
        }
    }

    /**
     * 转换历史实体为内存消息
     */
    fun convertToMemoryMessages(histories: List<HistoryRecord>): List<MemoryMessage> {
        return histories.mapNotNull { history ->
            history.content?.let {
                MemoryMessage(
                    id = history.id!!,
                    userId = history.userId,
                    groupId = history.groupId,
                    time = history.createdAt,
                    content = it
                )
            }
        }
    }

    /**
     * 记忆整理工具集 - 最小职责的工具方法
     * 向量同步已内联到各个操作工具中
     */
    class FactOrganizeTools(
        val memoryRepository: MemoryRepository,
        val factVectorStore: FactVectorStore,
        val botId: String,
        val groupId: String
    ) : ToolSet {

        /**
         * 新增事实到数据库，并同步创建向量索引
         */
        @Tool
        @LLMDescription("添加新的事实记忆到数据库（自动同步向量索引）")
        suspend fun addFact(
            @LLMDescription("事实关键词") keyword: String,
            @LLMDescription("事实描述") description: String,
            @LLMDescription("相关值/属性，逗号分隔") values: String,
            @LLMDescription("涉及的主体(用户ID)，逗号分隔") subjects: String,
            @LLMDescription("范围类型: USER 或 GROUP") scope: Scopes
        ): String {
            return try {
                val repo = memoryRepository
                val vectorStore = factVectorStore

                // 1. 添加到数据库
                withContext(Dispatchers.IO) {
                    repo.createFact(botId, groupId, keyword, description, values, subjects, scope)
                }

                // 2. 同步创建向量索引
                val newFact = withContext(Dispatchers.IO) {
                    repo.getLatestFact(
                        botId, groupId, keyword,
                        if (scope == Scopes.USER) MemoryScopes.USER else MemoryScopes.GROUP
                    )
                }
                newFact?.let {
                    val vectorId = vectorStore.indexFact(it)
                    withContext(Dispatchers.IO) {
                        repo.updateFactVectorId(it.id, vectorId)
                    }
                    log.debug("事实向量索引已创建, factId=${it.id}, vectorId=$vectorId")
                }

                "事实已添加: [$keyword] $description, 主体: $subjects, 向量已同步"
            } catch (e: Exception) {
                log.error("添加事实失败, groupId=$groupId", e)
                "添加失败: ${e.message}"
            }
        }

        /**
         * 标记过时事实为废弃，并删除向量索引
         */
        @Tool
        @LLMDescription("标记过时或不成立的事实为废弃（自动删除向量索引）")
        suspend fun deprecateFact(
            @LLMDescription("要废弃的事实的关键词") keyword: String,
            @LLMDescription("涉及的主体(用户ID)，逗号分隔") subjects: String,
            @LLMDescription("范围类型: USER 或 GROUP") scope: Scopes,
            @LLMDescription("废弃原因") reason: String = ""
        ): String {
            return try {
                val repo = memoryRepository
                val vectorStore = factVectorStore

                // 1. 标记废弃
                withContext(Dispatchers.IO) {
                    repo.deprecateFacts(botId, groupId, keyword, subjects, scope)
                }

                // 2. 删除向量索引
                val factEntity = withContext(Dispatchers.IO) {
                    repo.getFactByKeywordAndSubjects(
                        botId, groupId, keyword, subjects,
                        if (scope == Scopes.USER) MemoryScopes.USER else MemoryScopes.GROUP
                    )
                }
                factEntity?.let { entity ->
                    entity.vectorId?.let { vectorId ->
                        vectorStore.deleteVector(vectorId, botId, groupId)
                        log.debug("事实向量索引已删除, factId=${entity.id}, vectorId=$vectorId")
                    }
                }

                "事实已废弃: [$keyword], 主体: $subjects, 原因: $reason, 向量已删除"
            } catch (e: Exception) {
                log.error("废弃事实失败, groupId=$groupId", e)
                "废弃失败: ${e.message}"
            }
        }

        /**
         * 合并相似事实，保留最完整的一条
         */
        @Tool
        @LLMDescription("合并相似的多条事实，保留最完整的一条（自动处理向量索引）")
        suspend fun mergeFacts(
            @LLMDescription("被合并的事实ID") factIdToRemove: Int,
            @LLMDescription("保留的事实ID") factIdToKeep: Int,
            @LLMDescription("范围类型: USER 或 GROUP") scope: Scopes
        ): String {
            return try {
                val repo = memoryRepository
                val vectorStore = factVectorStore

                // 1. 获取被合并的事实并删除其向量
                val oldFact = withContext(Dispatchers.IO) {
                    repo.getFactById(factIdToRemove)
                }
                oldFact?.let { entity ->
                    entity.vectorId?.let { vectorId ->
                        vectorStore.deleteVector(vectorId, botId, groupId)
                        log.debug("合并事实向量索引已删除, factId=$factIdToRemove, vectorId=$vectorId")
                    }
                }

                // 2. 废弃被合并的事实
                withContext(Dispatchers.IO) {
                    repo.deprecateFactsById(botId, groupId, factIdToRemove, scope)
                }

                "事实已合并: $factIdToRemove -> $factIdToKeep, 向量已清理"
            } catch (e: Exception) {
                log.error("合并事实失败, groupId=$groupId", e)
                "合并失败: ${e.message}"
            }
        }

        /**
         * 更新事实内容
         */
        @Tool
        @LLMDescription("更新已有事实的内容（自动更新向量索引）")
        suspend fun updateFact(
            @LLMDescription("要更新的事实ID") factId: Int,
            @LLMDescription("新的关键词") newKeyword: String,
            @LLMDescription("新的描述") newDescription: String,
            @LLMDescription("新的相关值，逗号分隔") newValues: String,
            @LLMDescription("涉及的主体(用户ID)，逗号分隔") subjects: String,
            @LLMDescription("范围类型: USER 或 GROUP") scope: Scopes
        ): String {
            return try {
                val repo = memoryRepository
                val vectorStore = factVectorStore

                // 1. 获取旧事实并删除向量
                val oldFact = withContext(Dispatchers.IO) {
                    repo.getFactById(factId)
                }
                oldFact?.let { entity ->
                    entity.vectorId?.let { vectorId ->
                        vectorStore.deleteVector(vectorId, botId, groupId)
                        log.debug("更新事实旧向量索引已删除, factId=$factId, vectorId=$vectorId")
                    }
                }

                // 2. 废弃旧事实
                withContext(Dispatchers.IO) {
                    repo.deprecateFactsById(botId, groupId, factId, scope)
                }

                // 3. 添加新事实
                withContext(Dispatchers.IO) {
                    repo.createFact(botId, groupId, newKeyword, newDescription, newValues, subjects, scope)
                }

                // 4. 创建新向量
                val newFact = withContext(Dispatchers.IO) {
                    repo.getLatestFact(
                        botId, groupId, newKeyword,
                        if (scope == Scopes.USER) MemoryScopes.USER else MemoryScopes.GROUP
                    )
                }
                newFact?.let {
                    val vectorId = vectorStore.indexFact(it)
                    withContext(Dispatchers.IO) {
                        repo.updateFactVectorId(it.id, vectorId)
                    }
                    log.debug("更新事实新向量索引已创建, factId=${it.id}, vectorId=$vectorId")
                }

                "事实已更新: ID=$factId, [$newKeyword] $newDescription, 向量已更新"
            } catch (e: Exception) {
                log.error("更新事实失败, groupId=$groupId", e)
                "更新失败: ${e.message}"
            }
        }

    }

    /**
     * 整理群记忆 - 真正的 ReAct Agent
     * LLM 自主分析、决定调用工具、执行结果返回、循环直到完成
     */
    @OptIn(ExperimentalTime::class)
    suspend fun organize(
        botMark: String,
        groupId: String,
        messages: List<MemoryMessage>
    ) {
        if (messages.isEmpty()) return

        // 定义 ReAct 策略
        fun getMemoryStrategy(): AIAgentGraphStrategy<String, String> {
            return strategy("memory") {
                // 节点1: LLM 分析输入
                val nodeAnalyze by node<String, Message.Response> { input ->
                    llm.writeSession {
                        appendPrompt {
                            user {
                                text(input)
                            }
                        }
                        requestLLM()
                    }
                }

                // 节点2: 执行工具
                val nodeExecuteTool by nodeExecuteTool()

                // 节点3: 发送工具结果给 LLM
                val nodeSendToolResult by nodeLLMSendToolResult()

                // 边: 定义流程走向
                edge(nodeStart forwardTo nodeAnalyze)
                edge(nodeAnalyze forwardTo nodeFinish onAssistantMessage { true })  // 无工具调用，结束
                edge(nodeAnalyze forwardTo nodeExecuteTool onToolCall { true })    // 有工具调用
                // 工具执行后，如果结果有效则发送给 LLM，否则结束
                edge(nodeExecuteTool forwardTo nodeSendToolResult)
                edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })  // 继续调用工具
                edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true }) // 无更多工具，结束
            }
        }

        // 系统提示词
        val systemPrompt = prompt("记忆整理") {
            system(
                """
                你是记忆管理员。分析群聊消息和现有记忆，自主决定调用哪些工具来整理。

                ## 可用工具
                - addFact: 添加新事实（自动同步向量）
                - deprecateFact: 废弃过时事实（自动删除向量）
                - mergeFacts: 合并相似事实
                - updateFact: 更新事实

                ## 操作原则
                1. 只记录长久有效的信息（如职业、居住地、人际关系、重大经历、性格特质）
                2. 冲突时先 DEPRECATE 旧事实再 ADD 新事实
                3. 相似事实可以 MERGE
                4. 不准确信息可以 UPDATE
                5. 每执行一个操作后等待结果，再决定下一步
                6. 确认所有事实操作完成后，再输出最终总结

                ## 执行流程
                1. 基于分析结果决定需要调用哪些操作工具
                2. 每执行一个操作后查看结果，再决定下一步
                3. 所有操作完成后，输出完成总结

                开始分析！
                """.trimIndent()
            )
        }

        val factTools = FactOrganizeTools(memoryRepository, factVectorStore, botMark, groupId)

        // 格式化消息
        val msgText =
            messages.joinToString("\n") { "${DateTimeFormat.format(it.time)} | 主体: ${it.userId} | 内容: ${it.content}" }

        val facts = withContext(Dispatchers.IO) {
            memoryRepository.getFacts(botMark, groupId)
        }

        val factsText = facts.joinToString("\n") { fact ->
            "ID: ${fact.id} | 关键词: ${fact.keyword} | 内容: ${fact.description} | 主体: ${fact.subjects} | 范围: ${fact.scopeType}"
        }

        return try {
            // 创建 ReAct Agent
            val agent = AIAgent(
                promptExecutor = promptExecutor,
                agentConfig = AIAgentConfig(
                    prompt = systemPrompt,
                    model = LLMModelsChoice.Pro,
                    maxAgentIterations = 50
                ),
                toolRegistry = ToolRegistry {
                    tools(factTools.asTools())
                },
                strategy = getMemoryStrategy()
            )

            // 运行 Agent
            agent.run(
                """
                请分析以下消息，整理记忆：

                群聊消息:
                $msgText

                现有事实：
                $factsText
                """.trimIndent()
            )

            // 统计结果
            val finalFacts = withContext(Dispatchers.IO) {
                memoryRepository.getValidFacts(botMark, groupId)
            }

            log.info("ReAct Agent memory sorting completed, groupId=$groupId, number of existing facts=${finalFacts.size}")
        } catch (e: Exception) {
            log.error("Memory arrangement failed", e)
        }
    }
}
