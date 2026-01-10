package uesugi.core.emotion

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory
import uesugi.toolkit.DateTimeFormat

/**
 * PAD 情感量表 12 题评分数据模型
 *
 * 基于中国科学院心理研究所提出的中文简化 PAD 情感量表
 * - P (Pleasure): 愉悦度，Q1/Q4/Q7/Q10
 * - A (Arousal): 唤醒度，Q2/Q5/Q8/Q11
 * - D (Dominance): 掌控感，Q3/Q6/Q9/Q12
 */
@Serializable
@SerialName("PadScale12")
data class PadScale12(

    // ===== Pleasure (P) - 愉悦度 =====

    @property:LLMDescription(
        "Q1: 情感维度 P（愉悦度）。评分范围 [-4.00, 4.00]。-4 表示愤怒的，+4 表示有活力的。"
    )
    val q1: Double,

    @property:LLMDescription(
        "Q4: 情感维度 P（愉悦度）。评分范围 [-4.00, 4.00]。-4 表示友好的，+4 表示轻蔑的。"
    )
    val q4: Double,

    @property:LLMDescription(
        "Q7: 情感维度 P（愉悦度）。评分范围 [-4.00, 4.00]。-4 表示残忍的，+4 表示高兴的。"
    )
    val q7: Double,

    @property:LLMDescription(
        "Q10: 情感维度 P（愉悦度）。评分范围 [-4.00, 4.00]。-4 表示兴奋的，+4 表示激怒的。"
    )
    val q10: Double,


    // ===== Arousal (A) - 唤醒度 =====

    @property:LLMDescription(
        "Q2: 情感维度 A（唤醒度）。评分范围 [-4.00, 4.00]。-4 表示清醒的，+4 表示困倦的。"
    )
    val q2: Double,

    @property:LLMDescription(
        "Q5: 情感维度 A（唤醒度）。评分范围 [-4.00, 4.00]。-4 表示平静的，+4 表示激动的。"
    )
    val q5: Double,

    @property:LLMDescription(
        "Q8: 情感维度 A（唤醒度）。评分范围 [-4.00, 4.00]。-4 表示感兴趣的，+4 表示放松的。"
    )
    val q8: Double,

    @property:LLMDescription(
        "Q11: 情感维度 A（唤醒度）。评分范围 [-4.00, 4.00]。-4 表示放松的，+4 表示充满希望的。"
    )
    val q11: Double,


    // ===== Dominance (D) - 掌控感 =====

    @property:LLMDescription(
        "Q3: 情感维度 D（掌控感）。评分范围 [-4.00, 4.00]。-4 表示被控的，+4 表示主控的。"
    )
    val q3: Double,

    @property:LLMDescription(
        "Q6: 情感维度 D（掌控感）。评分范围 [-4.00, 4.00]。-4 表示支配的，+4 表示顺从的。"
    )
    val q6: Double,

    @property:LLMDescription(
        "Q9: 情感维度 D（掌控感）。评分范围 [-4.00, 4.00]。-4 表示被引导的，+4 表示自主的。"
    )
    val q9: Double,

    @property:LLMDescription(
        "Q12: 情感维度 D（掌控感）。评分范围 [-4.00, 4.00]。-4 表示有影响力的，+4 表示受影响的。"
    )
    val q12: Double
)

/**
 * 群聊消息数据模型
 *
 * @property serial 消息序号
 * @property userId 用户ID
 * @property role 角色(自己/他人)
 * @property time 发送时间
 * @property content 消息内容
 */
data class GMessage(
    val serial: Int,
    val userId: String,
    val role: Role,
    val time: LocalDateTime,
    val content: String
) {
    /**
     * 消息角色枚举
     */
    enum class Role(val value: String) {
        OTHER("他人"),
        SELF("自己")
    }

    /**
     * 转换为 LLM Prompt 格式
     * 格式: [序号 用户ID 角色 时间] 内容
     */
    fun asLlmPrompt() = "[$serial $userId ${role.value} ${time.format(DateTimeFormat)}] $content"
}

/**
 * 构建情感分析 Prompt
 *
 * @param history 群聊历史消息列表
 * @return Prompt 对象
 */
fun buildPrompt(history: List<GMessage>) = prompt(
    "提取群聊消息的情感 PAD 数值"
) {
    system(
        """
        你是一名情绪量表评估助手。
        
        请你使用
        "中国科学院心理研究所提出的中文简化 PAD 情感量表"
        对【群聊消息】中说话者的即时情绪状态进行评估。
        
        请将该消息视为一次"心理量表作答情境"，
        根据消息的内容、语气和群聊表达方式，
        对 Q1–Q12 共 12 个项目逐项评分。
        
        评分规则：
        - 每一题的评分范围为 [-4.00, +4.00]
        - 数值越接近左侧，越偏向左侧情感词
        - 数值越接近右侧，越偏向右侧情感词
        - 0 表示介于两者之间或无法判断
        - 所有评分均反映"说话者当下的主观情绪状态"
        - 不推断长期人格或动机
        - 信息不足时，请给接近 0 的分值
        
        请对以下项目进行评分：
        
        Q1  愤怒的    ←→    有活力的
        Q2  清醒的    ←→    困倦的
        Q3  被控的    ←→    主控的
        Q4  友好的    ←→    轻蔑的
        Q5  平静的    ←→    激动的
        Q6  支配的    ←→    顺从的
        Q7  残忍的    ←→    高兴的
        Q8  感兴趣的  ←→    放松的
        Q9  被引导的  ←→    自主的
        Q10 兴奋的    ←→    激怒的
        Q11 放松的    ←→    充满希望的
        Q12 有影响力  ←→    被影响的
        
        输出要求：
        - 仅输出 JSON
        - 包含 Q1 至 Q12 的评分
        - 所有数值为两位小数
        - 不要输出 PAD 维度结果
        - 不要输出解释性文字
        """.trimIndent()
    )

    val msg = history.joinToString("\n") { message ->
        message.asLlmPrompt()
    }

    user(
        """
        请根据以下群聊历史记录，分析群聊氛围，并计算机器人在当前群聊环境下的即时情绪状态。
        
        你将处理群聊历史记录，每条记录的格式如下：
        [序号 群成员ID 角色 时间] 内容
        
        说明：
        - 序号：从1开始的数字
        - 群成员ID：发送消息的用户ID
        - 角色：标明消息是"他人"还是"自己"
        - 时间：消息发送时间，格式为 yyyy-MM-dd HH:mm:ss
        - 内容：消息文本
        
        群聊消息：
        $msg
        
        规则：
        1. 你需要根据**群聊内容、语气、成员互动和整体氛围**来判断机器人此刻的情绪。
        2. 情绪评估使用"中国科学院心理研究所提出的中文简化 PAD 情感量表"。
        3. 对 Q1–Q12 共 12 个项目评分：
           - 范围 [-4.00, +4.00]
           - 数值越接近左侧，越偏向左侧情感词
           - 数值越接近右侧，越偏向右侧情感词
           - 0 表示介于两者之间或无法判断
        4. 评分仅反映机器人在当前氛围下的即时主观情绪，不推断长期人格或动机。
        """.trimIndent()
    )
}

/**
 * 分析情感刺激值
 *
 * 根据群聊历史消息，使用 LLM 分析并计算 PAD 情感刺激值
 *
 * @param history 群聊历史消息列表
 * @return Stimulus (PAD 情感刺激值)
 */
suspend fun analyzeStimulus(history: List<GMessage>): Stimulus {
    val log = LoggerFactory.getLogger("EmotionAgent")

    log.debug("开始分析情感刺激值, 消息数=${history.size}")

    // 构建 Prompt
    val prompt = buildPrompt(history)

    // 获取 Prompt 执行器
    val promptExecutor by GlobalContext.get().inject<PromptExecutor>()

    // 执行结构化 LLM 调用
    val result = promptExecutor.executeStructured<PadScale12>(
        prompt = prompt,
        model = GoogleModels.Gemini2_5Pro,
        fixingParser = StructureFixingParser(
            model = GoogleModels.Gemini2_5FlashLite,
            retries = 2
        )
    )

    // 提取 PAD 量表评分
    val padScale12 = result.getOrThrow().data

    log.debug("PAD 量表评分: Q1=${padScale12.q1}, Q4=${padScale12.q4}, Q7=${padScale12.q7}, Q10=${padScale12.q10}")
    log.debug("PAD 量表评分: Q2=${padScale12.q2}, Q5=${padScale12.q5}, Q8=${padScale12.q8}, Q11=${padScale12.q11}")
    log.debug("PAD 量表评分: Q3=${padScale12.q3}, Q6=${padScale12.q6}, Q9=${padScale12.q9}, Q12=${padScale12.q12}")

    // 转换为 PAD 三维值
    val stimulus = Stimulus.from(padScale12)

    log.debug(
        "情感刺激值分析完成, P=${String.format("%.2f", stimulus.p)}, A=${
            String.format(
                "%.2f",
                stimulus.a
            )
        }, D=${String.format("%.2f", stimulus.d)}"
    )

    return stimulus
}