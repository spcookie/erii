package uesugi.core.volition

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
import uesugi.core.emotion.EmotionalTendencies
import uesugi.toolkit.DateTimeFormat
import uesugi.toolkit.JSON
import uesugi.toolkit.logger


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

    suspend fun analysis(
        messages: List<VolitionMessage>,
        botInterests: String,
        mood: EmotionalTendencies
    ): StimulusAnalysis? {
        if (messages.isEmpty()) return null

        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()
        val messagesText = messages.joinToString("\n") { it.asLlmPrompt() }

        val prompt = prompt("主动行为分析") {
            system(
                """
                你是一名"群聊主动行为分析器"，用于判断我是否应该主动插话。
                
                你的任务是：
                - 分析最近的群聊消息
                - 判断是否存在外部刺激（关键词命中、热闹场景、间接提及等）
                - 结合我当前的情绪状态和冲动值
                - 决策是否应该主动发言，以及使用什么语气
                
                ⚠️ 重要约束：
                1. 主动发言应该自然，不要显得突兀
                2. 避免在严肃话题时插话
                3. 输出必须是严格 JSON
                """.trimIndent()
            )

            user(
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

        return try {
            val response = promptExecutor.executeStructured<StimulusAnalysis>(
                prompt,
                model = GoogleModels.Gemini2_5Flash,
                fixingParser = StructureFixingParser(
                    model = GoogleModels.Gemini2_5FlashLite,
                    retries = 2
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