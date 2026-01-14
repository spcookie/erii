package uesugi.core.memory

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext
import uesugi.core.history.HistoryEntity
import uesugi.toolkit.DateTimeFormat
import uesugi.toolkit.JSON
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
        val action: MemoryAction,   // ADD / DEPRECATE
        val reason: String,         // 为什么这么判断
        val keyword: String,        // 关键词
        val description: String,    // 事实描述
        val values: List<String>,   // 相关值/属性
        val subjects: List<String>, // 涉及的主体(用户ID)
        val scopeType: MemoryScopes, // 范围类型: USER/GROUP
        val confidence: Double,     // 置信度, 范围是 0.0-1.0
    )

    @Serializable
    data class FactsAnalysisInput(
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
                
                请分析并输出以下 4 个字段：
                
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
            model = GoogleModels.Gemini2_5Flash,
            fixingParser = StructureFixingParser(
                model = GoogleModels.Gemini2_5FlashLite,
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
     * @return 事实记忆列表
     */
    suspend fun extractFacts(messages: List<MemoryMessage>, facts: List<FactsAnalysisInput>): List<FactsAnalysis> {
        log.debug("开始提取事实记忆, 消息数=${messages.size}")

        val prompt = prompt("提取事实记忆") {
            system(
                """
                你是一名长期记忆系统中的“事实裁决专家”。
                
                你将收到：
                1. 最近的群聊消息
                2. 当前已存在的相关记忆事实（如果有）
                
                你的任务是：
                判断群聊中是否出现了**新的事实**，或者**已有事实已经不再成立**。
                
                【事实定义】
                事实是可以被明确验证的信息，包括：
                - 人物关系
                - 明确发生的事件
                - 稳定偏好（但可能变化）
                - 明确状态（通常具有时效性）
                
                【记忆操作规则】
                
                你只能使用以下两种操作：
                
                1. ADD  
                - 新出现的、此前不存在的事实
                
                2. DEPRECATE  
                - 明确被否定
                - 明确已经结束
                - 被新事实取代
                - 明显不再适用（如“最近”“目前”）
                
                ⚠️ 不允许凭感觉废弃事实  
                ⚠️ 不允许推断未明说的信息  
                
                【输出要求】
                
                - 每个事实必须包含：
                  - action: ADD 或 DEPRECATE
                  - reason: 简要说明判断原因
                  - keyword
                  - description（20字左右）
                  - values
                  - subjects
                  - scopeType: USER 或 GROUP
                  - confidence: 0~1
                
                - 总数控制在 5~15 条
                - 只输出 JSON，不要任何解释
                """.trimIndent()
            )

            val msg = messages.joinToString("\n") { it.asLlmPrompt() }
            user(
                """
                已存在的相关记忆事实：
                ${JSON.encodeToString(facts)}
                    
                群聊历史消息:
                $msg
                
                请提取其中的事实记忆。
                """.trimIndent()
            )
        }

        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()

        val result = promptExecutor.executeStructured<FactsAnalysisList>(
            prompt = prompt,
            model = GoogleModels.Gemini2_5Flash,
            fixingParser = StructureFixingParser(
                model = GoogleModels.Gemini2_5FlashLite,
                retries = 2
            )
        )

        return result.getOrThrow().data.facts.also {
            log.debug("事实记忆提取完成, 提取数量=${it.size}")
        }
    }

    /**
     * 生成 Todo 事项(意图驱动)
     *
     * @param messages 按用户分组的历史消息
     * @param userId 目标用户ID
     * @return Todo 事项列表
     */
    suspend fun generateTodos(messages: List<MemoryMessage>, userId: String): List<TodoAnalysis> {
        log.debug("开始生成 Todo 事项, userId=$userId, 消息数=${messages.size}")

        val prompt = prompt("生成 Todo 事项") {
            system(
                """
                你是一名【群聊意图识别专家】，负责从群聊消息中提取【机器人可以主动介入并推动后续行动的意图】。
                
                你的输出将被用于：
                - 机器人主动提醒 / 组局 / 提议
                - 待办（Todo）生成
                - 群聊互动触发
                
                【核心定义（非常重要）】
                
                一个“可推动意图”必须同时满足：
                
                1. **存在后续行动空间**
                   - 机器人介入后，可以促成“讨论、组织、决策或行动”
                2. **通常需要或受益于他人参与**
                   - 如：一起、组局、征求意见、安排时间
                3. **不是自动完成的个人行为**
                   - 不应是个人马上就会做完的事
                
                【明确排除的内容（禁止提取）】
                
                以下内容一律不得作为 Todo：
                
                - 纯生理或日常状态  
                  如：睡觉、吃饭、起床、上班、下班、洗澡、困了
                
                - 已确定或无需推动的个人安排  
                  如：我去睡了、我现在去吃饭、我等下打游戏
                
                - 情绪或感受表达但无行动指向  
                  如：好累、烦死了、笑死
                
                - 无时间或行动窗口的随口一提  
                  如：哪天想去旅游、有点想学吉他
                
                【可提取的典型意图示例】
                
                ✔ 想一起去看某部电影  
                ✔ 讨论是否要聚餐 / 组局 / 出游  
                ✔ 表达近期有空，隐含可安排活动  
                ✔ 询问他人是否有兴趣参与某事  
                ✔ 群体决策尚未完成（去哪、什么时候）
                
                【提取规则】
                
                1. 只提取机器人**介入后能产生价值**的意图
                2. 每条意图必须是**具体、可执行、可推进**
                3. 数量限制：3–8 条
                4. 每条 Todo 包含字段：
                   - content: 意图的自然语言摘要
                   - priority: 1–10（时间敏感或多人相关优先）
                   - category: 业务相关分类（电影 / 美食 / 活动 / 出行 / 学习 等）
                   - relatedUserId: 发起该意图的用户 ID
                   - expireHours:
                       - 有明显时间窗口 → 合理小时数
                       - 无明确期限 → null
                
                【输出格式（严格遵守）】
                
                仅输出 JSON 数组，不得附加任何解释说明
                """.trimIndent()
            )

            val msg = messages.joinToString("\n") { it.asLlmPrompt() }
            user(
                """
                目标用户ID: $userId
                
                历史消息:
                $msg
                
                请提取可生成 Todo 的意图。
                """.trimIndent()
            )
        }

        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()

        val result = promptExecutor.executeStructured<TodoAnalysisList>(
            prompt = prompt,
            model = GoogleModels.Gemini2_5Flash,
            fixingParser = StructureFixingParser(
                model = GoogleModels.Gemini2_5FlashLite,
                retries = 2
            )
        )

        return result.getOrThrow().data.todos.also {
            log.debug("Todo 事项生成完成, 数量=${it.size}")
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
            model = GoogleModels.Gemini2_5Flash,
            fixingParser = StructureFixingParser(
                model = GoogleModels.Gemini2_5FlashLite,
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
