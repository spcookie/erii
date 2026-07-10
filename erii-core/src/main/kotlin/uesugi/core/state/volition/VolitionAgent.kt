package uesugi.core.state.volition

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.params.LLMParams
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext
import uesugi.common.LLMModelChoice
import uesugi.common.data.EmotionalTendencies
import uesugi.common.toolkit.DateTimeFormat
import uesugi.common.toolkit.JSON
import uesugi.common.toolkit.logger
import kotlin.time.ExperimentalTime


@Serializable
@SerialName("StimulusAnalysis")
@LLMDescription("外部刺激分析，判断当前群聊环境对机器人的刺激程度")
data class StimulusAnalysis(
    @property:LLMDescription("是否命中关键词，true表示消息中包含机器人感兴趣的关键词")
    val keywordHit: Boolean,
    @property:LLMDescription("关键词刺激强度，0.0-1.0，值越大表示相关度越高")
    val keywordStrength: Double,
    @property:LLMDescription("是否为热闹场景，true表示群消息频率高")
    val isBusy: Boolean,
    @property:LLMDescription("是否被间接提及，true表示讨论内容涉及机器人")
    val indirectMention: Boolean,
    @property:LLMDescription("是否存在情绪共鸣，true表示群体情绪与机器人当前情绪匹配")
    val emotionalResonance: Boolean
)

data class VolitionMessage(
    val id: Int,
    val botId: String,
    val groupId: String,
    val userId: String,
    val time: LocalDateTime,
    val content: String
) {
    fun asLlmPrompt() = "[ID:$id $userId ${time.format(DateTimeFormat)}] $content"
}

class VolitionAgent {
    companion object {
        private val log = logger()
    }

    @OptIn(ExperimentalTime::class)
    suspend fun analysis(
        messages: List<VolitionMessage>,
        botInterests: String,
        mood: EmotionalTendencies
    ): StimulusAnalysis? {
        if (messages.isEmpty()) return null

        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()
        val messagesText = messages.joinToString("\n") { it.asLlmPrompt() }

        val prompt = prompt("__volition_analysis__", LLMParams(maxTokens = 65536)) {
            system(
                """
                你是一名"群聊外部刺激分析器"，用于判断最近消息是否增加机器人的主动发言刺激值。
                
                你的任务是：
                - 分析最近的群聊消息
                - 判断是否存在外部刺激（关键词命中、热闹场景、间接提及等）
                - 结合我当前的情绪状态判断是否存在情绪共鸣
                - 只输出外部刺激信号，不要决策是否应该主动发言
                
                ⚠️ 重要约束：
                1. keywordHit 只表示命中我的核心兴趣，不表示最终应该发言
                2. indirectMention 只在群友明确讨论我、喊我、评价我或询问我相关内容时为 true
                3. emotionalResonance 只在群体情绪和我的当前情绪明显同频时为 true
                4. 严肃话题、争吵、负面事件本身不应自动算作正向刺激
                5. 输出必须是严格 JSON
                """.trimIndent()
            )

            user {
                text(
                    """
                    分析群聊外部刺激。根据系统消息中的指示判断：是否存在关键词命中、热闹场景、间接提及、情绪共鸣，输出结构化JSON。

                    数据如下：
                    """.trimIndent()
                )
                text(
                    """
                    【我的核心兴趣领域】
                    $botInterests

                    【我的当前情绪】
                    mood: ${mood.name}

                    【最近群聊消息】
                    以下是最近的群聊消息，按时间顺序排列：
                    $messagesText
                    """.trimIndent()
                )
            }
        }

        return try {
            val response = promptExecutor.executeStructured<StimulusAnalysis>(
                prompt,
                model = LLMModelChoice.Pro,
                fixingParser = StructureFixingParser(
                    model = LLMModelChoice.Lite,
                    retries = 2
                ),
                examples = listOf(
                    StimulusAnalysis(
                        keywordHit = true,
                        keywordStrength = 0.8,
                        isBusy = true,
                        indirectMention = true,
                        emotionalResonance = true
                    )
                )
            )

            val result = response.getOrThrow().data

            log.debug("主动行为分析完成: stimulus analysis ${JSON.encodeToString(result)}")

            result
        } catch (e: Exception) {
            log.error("主动行为分析失败", e)
            null
        }
    }
}
