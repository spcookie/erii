package uesugi.core.state.emotion

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory
import uesugi.common.DateTimeFormat
import uesugi.common.LLMModelsChoice
import uesugi.common.PadScale12
import kotlin.time.ExperimentalTime

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
@OptIn(ExperimentalTime::class)
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
        model = LLMModelsChoice.Pro,
        fixingParser = StructureFixingParser(
            model = LLMModelsChoice.Lite,
            retries = 2
        ),
        examples = listOf(
            PadScale12(
                q1 = 0.1, q4 = 2.0, q7 = -3.0, q10 = 0.0,
                q2 = 0.0, q5 = 0.0, q8 = 0.0, q11 = 1.1,
                q3 = 0.12, q6 = 0.99, q9 = 0.0, q12 = 0.0
            )
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