package uesugi.core.state.emotion

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory
import uesugi.common.LLMProviderChoice
import uesugi.common.data.PadScale12
import uesugi.common.toolkit.DateTimeFormat
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
    "__emotion_analysis__",
    LLMParams(maxTokens = 65536)
) {
    system(
        """
        你使用"中文简化 PAD 情感量表"（中国科学院心理研究所）评估群聊场景下的即时情绪。

        根据群聊消息的**内容、语气、互动氛围**，对 Q1–Q12 逐项评分：

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

        规则：
        - 每题范围 [-4.00, +4.00]，越靠近左侧越偏左侧词，越靠近右侧越偏右侧词
        - 评分反映机器人**当下即时主观情绪**，不推断长期人格或动机
        - 根据对话内容、语气、互动氛围**合理推测**机器人的情绪反应，避免给出全 0 的打分

        仅输出 JSON，含 Q1–Q12，保留两位小数，无解释。
        """.trimIndent()
    )

    val msg = history.joinToString("\n") { message ->
        message.asLlmPrompt()
    }

    user {
        text(
            """
            请根据以下群聊记录，评估机器人此刻的情绪状态。

            群聊消息：
            $msg
            """.trimIndent()
        )
    }
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
        model = LLMProviderChoice.Pro,
        fixingParser = StructureFixingParser(
            model = LLMProviderChoice.Lite,
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
