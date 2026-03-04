package uesugi.core.state.memory

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext
import uesugi.config.LLMModelsChoice
import uesugi.core.message.history.HistoryEntity
import uesugi.toolkit.DateTimeFormat
import uesugi.toolkit.logger

/**
 * 记忆代理 - 负责从历史消息中提取和生成各类记忆数据
 */
class MemoryAgent {

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
    data class UserProfileAnalysis(
        val userId: String,
        val profile: String,        // 用户性格、行为特征
        val preferences: String,    // 兴趣偏好
    )

    /**
     * 事实记忆提取结果
     */
    @Serializable
    data class FactsAnalysis(
        val id: Int,
        val action: MemoryAction,   // ADD / DEPRECATE
        val reason: String,         // 为什么这么判断
        val keyword: String,        // 关键词
        val description: String,    // 事实描述
        val values: List<String>,   // 相关值/属性
        val subjects: List<String>, // 涉及的主体(用户ID)
        val scopeType: MemoryScopes, // 范围类型: USER/GROUP
        val confidence: Double
    )

    @Serializable
    data class FactsAnalysisInput(
        val id: Int,
        val keyword: String,        // 关键词
        val description: String,    // 事实描述
        val values: List<String>,   // 相关值/属性
        val subjects: List<String>, // 涉及的主体(用户ID)
        val scopeType: MemoryScopes // 范围类型: USER/GROUP
    )

    @Serializable
    enum class MemoryAction {
        ADD,
        DEPRECATE
    }

    enum class MemoryScopes {
        USER,
        GROUP
    }

    @Serializable
    data class FactsAnalysisList(
        val facts: List<FactsAnalysis>
    )

    /**
     * Todo 事项提取结果
     */
    @Serializable
    data class TodoAnalysis(
        val content: String,           // 意图内容
        val priority: Int,             // 优先级 1-10
        val category: String?,         // 分类
        val relatedUserId: String?,    // 关联用户
        val expireHours: Int?          // 过期小时数
    )

    @Serializable
    data class TodoAnalysisList(
        val todos: List<TodoAnalysis>
    )

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
    suspend fun analyzeUserProfile(
        messages: List<MemoryMessage>,
        userProfileEntity: UserProfileEntity?
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
                    append("历史消息:\n$msg")
                    append("请分析该用户的画像和偏好。")
                    append("只输出该用户当前最新的画像和偏好。")
                }
            )
        }

        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()

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
     * 提取事实记忆
     *
     * @param messages 历史消息列表
     * @param facts 当前已存在的相关事实（输入给 LLM 做参考）
     * @return 事实记忆操作列表
     */
    suspend fun extractFacts(messages: List<MemoryMessage>, facts: List<FactsAnalysisInput>): List<FactsAnalysis> {
        log.debug("开始提取事实记忆, 消息数=${messages.size}, 现有事实数=${facts.size}")

        // 1. 预处理现有事实，使其对 LLM 更易读
        val existingFactsContext = if (facts.isEmpty()) {
            "当前无相关历史记忆。"
        } else {
            facts.joinToString("\n") {
                "ID: ${it.id} | 内容: ${it.description} | 主体: ${it.subjects} | 值: ${it.values}"
            }
        }

        // 2. 预处理消息，带上发送者信息
        val msgContext = messages.joinToString("\n") {
            "[${it.userId}]: ${it.content}"
        }

        val prompt = prompt("提取事实记忆") {
            system(
                """
            你是一个由高级人工智能驱动的“长期记忆管理员”。你的核心目标是**维护记忆库的一致性和时效性**。
            
            你拥有两份数据：
            1. [现有记忆库]：已经存储的事实（带有 ID）。
            2. [最新群聊]：刚刚发生的对话。

            你的任务是分析对话，对记忆库执行操作。
            
            ### 核心原则
            1. **高价值原则**：只记录长久有效的信息（如职业、居住地、人际关系、重大经历、性格特质）。忽略闲聊、情绪宣泄或临时状态（如“我在吃饭”、“哈哈哈哈”）。
            2. **动态更新原则**：当新信息与[现有记忆库]冲突时，必须 **DEPRECATE（废弃）** 旧事实，并 **ADD（新增）** 新事实。
            3. **置信度原则**：不确定的推测不要记录。

            ### 操作指令详解
            
            **Action: ADD**
            - 场景：出现了此前未记录的新事实，或作为旧事实的更新版。
            - 要求：必须提取准确的 values 和 subjects。
            
            **Action: DEPRECATE**
            - 场景：
              1. **状态变更**：旧事实不再成立（例如：搬家了、分手了、换工作了）。
              2. **纠错**：用户明确表示之前的信息是错的。
            - ⚠️ **重要**：如果发生状态变更（如从“单身”变为“恋爱”），你必须输出两条记录：一条 DEPRECATE 旧 ID，一条 ADD 新状态。
            
            ### JSON 输出字段说明
            - action: "ADD" | "DEPRECATE"
            - id: 仅 DEPRECATE 时必须填写对应[现有记忆库]中的 ID。ADD 时留空。
            - reason: **(关键)** 必须先简述推理过程。例如："用户明确说搬到了上海，与旧记忆(ID 101 北京)冲突，故废弃旧记忆并新增。"
            - description: 事实的自然语言描述（第三人称，例如 "UserA 现在的职业是医生"）。
            - subjects: 相关主体列表（如 ["UserA", "UserB"]）。
            - scopeType: "USER" (个人属性) | "GROUP" (群组共识/规则)。
            - confidence: 0.0 ~ 1.0
            
            ### 少样本示例 (Few-Shot)
            
            [现有记忆库]
            ID: 101 | 内容: Alice 住在伦敦
            
            [最新群聊]
            [Alice]: 我终于搬到纽约了，刚落地。
            
            [输出]
            [
              {
                "action": "DEPRECATE",
                "id": 101,
                "reason": "Alice 明确表示搬到了纽约，'住在伦敦'已成过去式。",
                "description": "Alice 住在伦敦",
                ...
              },
              {
                "action": "ADD",
                "reason": "Alice 说明了新的居住地。",
                "description": "Alice 目前居住在纽约",
                "subjects": ["Alice"],
                "values": ["纽约"],
                ...
              }
            ]
            """.trimIndent()
            )

            user(
                """
            === [现有记忆库] ===
            $existingFactsContext
                
            === [最新群聊] ===
            $msgContext
            
            请基于以上信息生成 JSON 操作列表。
            """.trimIndent()
            )
        }

        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()

        // 使用 Flash 模型速度快，但需要结构化强制
        val result = promptExecutor.executeStructured<FactsAnalysisList>(
            prompt = prompt,
            model = LLMModelsChoice.Flash,
            fixingParser = StructureFixingParser(
                model = LLMModelsChoice.Lite,
                retries = 2
            )
        )

        return result.getOrThrow().data.facts.also { list ->
            log.debug("提取结束: 新增/更新=${list.count { it.action == MemoryAction.ADD }}, 废弃=${list.count { it.action == MemoryAction.DEPRECATE }}")
        }
    }

    /**
     * 生成对话摘要
     *
     * @param messages 历史消息列表
     * @param groupId 范围ID(群ID或用户ID)
     * @return 摘要分析结果
     */
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
    fun convertToMemoryMessages(histories: List<HistoryEntity>): List<MemoryMessage> {
        return histories.mapNotNull { history ->
            history.content?.let {
                MemoryMessage(
                    id = history.id.value,
                    userId = history.userId,
                    groupId = history.groupId,
                    time = history.createdAt,
                    content = it
                )
            }
        }
    }
}
