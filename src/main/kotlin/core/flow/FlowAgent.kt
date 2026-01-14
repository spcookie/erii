package uesugi.core.flow

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.structure.StructureFixingParser
import ai.koog.prompt.structure.executeStructured
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.context.GlobalContext
import uesugi.BotManage
import uesugi.toolkit.DateTimeFormat
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger

enum class ChargeEventType {
    CoreInterest,
    GroupResonance,
    DeepReply,
    ContinuousInteraction
}

enum class DrainEventType {
    TopicInterrupt,
    Negative,
    RepeatTopic,
    LowActivity
}

@Serializable
@SerialName("TopicAnalysis")
@LLMDescription("话题分析结果，判断对话是否围绕当前话题")
data class TopicAnalysis(
    @property:LLMDescription("是否在当前话题上，true表示仍在讨论当前话题")
    val isOnTopic: Boolean,
    @property:LLMDescription("话题漂移程度，0.0-1.0，值越大表示偏离越严重")
    val topicDriftLevel: Double,
    @property:LLMDescription("修正后的话题描述，用于下一轮心流判定")
    val revisedTopic: String
)

@Serializable
@SerialName("InterestMatch")
@LLMDescription("兴趣匹配分析，判断是否命中机器人的核心兴趣领域")
data class InterestMatch(
    @property:LLMDescription("是否命中核心兴趣，true表示当前对话涉及机器人感兴趣的话题")
    val hit: Boolean,
    @property:LLMDescription("兴趣匹配分数，1.0-50.0，值越大表示匹配度越高")
    val score: Double,
    @property:LLMDescription("匹配的兴趣关键词列表，例如：['并发控制', '系统设计']")
    val matchedReasons: List<String>
)

@Serializable
@SerialName("InteractionQuality")
@LLMDescription("互动质量分析，评估对话的深度和质量")
data class InteractionQuality(
    @property:LLMDescription("是否存在深度回复，true表示有分析性观点或深入追问")
    val deepReplies: Boolean,
    @property:LLMDescription("判断理由的文字说明")
    val reason: String
)

@Serializable
@SerialName("GroupResonance")
@LLMDescription("群体共鸣分析，判断多人是否围绕相似话题展开讨论")
data class GroupResonance(
    @property:LLMDescription("是否存在群体共鸣，true表示多人围绕相似语义话题讨论")
    val exists: Boolean,
    @property:LLMDescription("参与讨论的人数")
    val participants: Int,
    @property:LLMDescription("情绪PAD的 arousal，0.0-1.0，值越大表示愉悦度越强")
    val arousal: Double
)

@Serializable
@SerialName("NegativeSignals")
@LLMDescription("负面信号检测，识别对话中的负面刺激")
data class NegativeSignals(
    @property:LLMDescription("是否存在负面信号，true表示检测到负面内容")
    val exists: Boolean,
    @property:LLMDescription("负面信号类型列表，例如：['争吵', '攻击性语言', '冷嘲热讽']")
    val types: List<String>
)

@Serializable
@SerialName("Repetition")
@LLMDescription("重复话题检测，判断是否在无信息增量地重复讨论")
data class Repetition(
    @property:LLMDescription("是否存在重复，true表示检测到无信息增量的重复")
    val exists: Boolean,
    @property:LLMDescription("重复置信度，0.0-1.0，值越大表示重复程度越高")
    val confidence: Double
)

@Serializable
@SerialName("FlowSuggestions")
@LLMDescription("心流事件建议，指示应该触发哪些心流增量或消耗事件")
data class FlowSuggestions(
    @property:LLMDescription("应触发的心流增量事件类型列表")
    val shouldCharge: List<ChargeEventType>,
    @property:LLMDescription("应触发的心流消耗事件类型列表")
    val shouldDrain: List<DrainEventType>
)

@Serializable
@SerialName("FlowAnalysisResult")
@LLMDescription("群聊心流状态分析的完整结果")
data class FlowAnalysisResult(
    @property:LLMDescription("话题分析结果")
    val topicAnalysis: TopicAnalysis,
    @property:LLMDescription("兴趣匹配分析")
    val interestMatch: InterestMatch,
    @property:LLMDescription("互动质量分析")
    val interactionQuality: InteractionQuality,
    @property:LLMDescription("群体共鸣分析")
    val groupResonance: GroupResonance,
    @property:LLMDescription("负面信号检测")
    val negativeSignals: NegativeSignals,
    @property:LLMDescription("重复话题检测")
    val repetition: Repetition,
    @property:LLMDescription("心流事件触发建议")
    val flowSuggestions: FlowSuggestions
)

data class FlowMessage(
    val id: Int,
    val groupId: String,
    val userId: String,
    val time: LocalDateTime,
    val content: String
) {
    fun asLlmPrompt() = "[ID:$id $userId ${time.format(DateTimeFormat)}] $content"
}

class FlowAgent {
    companion object {
        private val log = logger()
    }

    suspend fun analysis(messages: List<FlowMessage>, botMark: String, groupId: String) {
        if (messages.isEmpty()) return

        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()

        val messagesText = messages.joinToString("\n") { it.asLlmPrompt() }

        val currentTopic = loadCurrentTopic(botMark, groupId)

        val botInterests = BotManage.getBot(botMark)?.role?.character

        val prompt = prompt("心流分析") {
            system(
                """
                你是一名"群聊对话状态分析器"，用于为 AI 机器人计算心流状态。
                
                你的任务是：
                - 判断对话是否仍然围绕当前话题
                - 判断是否命中机器人的核心兴趣
                - 判断互动质量、群体共鸣、负面刺激等
                - 输出结构化 JSON，用于程序直接消费
                
                ⚠️ 重要约束：
                1. 判断应偏保守，宁可不给高分，也不要滥触发
                2. 群体共鸣 ≠ 多人说话，而是【多人围绕相似语义话题】
                3. 重复话题指"无信息增量的重复"，不是正常延续
                4. 输出必须是 **严格 JSON**，不要任何额外说明文字
                
                输出 JSON 结构
                """.trimIndent()
            )

            user(
                """
                    【当前话题（上一轮总结）】
                    $currentTopic
                    
                    【机器人的核心兴趣领域】
                    $botInterests
                    
                    【最近 1 分钟的群聊消息】
                    以下是最近 1 分钟内的群聊消息，按时间顺序排列：
                    $messagesText
                    """.trimIndent()
            )
        }

        try {
            val response = promptExecutor.executeStructured<FlowAnalysisResult>(
                prompt,
                model = GoogleModels.Gemini2_5Flash,
                fixingParser = StructureFixingParser(
                    model = GoogleModels.Gemini2_5FlashLite,
                    retries = 2
                )
            )

            val result = response.getOrThrow().data

            persistAnalysisResult(botMark, groupId, result)

            triggerFlowEvents(result, botMark, groupId)

            log.debug("心流分析完成, groupId=$groupId, 当前话题=${result.topicAnalysis.revisedTopic}")
        } catch (e: Exception) {
            log.error("心流分析失败, groupId=$groupId", e)
        }
    }

    private fun triggerFlowEvents(result: FlowAnalysisResult, botMark: String, groupId: String) {
        result.flowSuggestions.shouldCharge
            .distinct()
            .forEach { eventType ->
                when (eventType) {
                    ChargeEventType.CoreInterest -> EventBus.postAsync(
                        CoreInterestEvent(
                            botMark,
                            groupId,
                            result.interestMatch.score
                        )
                    )

                    ChargeEventType.GroupResonance -> EventBus.postAsync(
                        GroupResonanceEvent(
                            botMark,
                            groupId,
                            result.groupResonance.arousal
                        )
                    )

                    ChargeEventType.DeepReply -> EventBus.postAsync(DeepReplyEvent(botMark, groupId))
                    ChargeEventType.ContinuousInteraction -> EventBus.postAsync(
                        ContinuousInteractionEvent(
                            botMark,
                            groupId
                        )
                    )
                }
            }

        result.flowSuggestions.shouldDrain
            .distinct()
            .forEach { eventType ->
                when (eventType) {
                    DrainEventType.TopicInterrupt -> EventBus.postAsync(TopicInterruptEvent(botMark, groupId))
                    DrainEventType.Negative -> EventBus.postAsync(NegativeEvent(botMark, groupId))
                    DrainEventType.RepeatTopic -> EventBus.postAsync(RepeatTopicEvent(botMark, groupId))
                    DrainEventType.LowActivity -> EventBus.postAsync(LowActivityEvent(botMark, groupId))
                }
            }

        if (!result.topicAnalysis.isOnTopic && result.topicAnalysis.topicDriftLevel > 0.6) {
            EventBus.postAsync(TopicInterruptEvent(botMark, groupId))
        }

        if (result.negativeSignals.exists) {
            EventBus.postAsync(NegativeEvent(botMark, groupId))
        }

        if (result.repetition.exists && result.repetition.confidence > 0.7) {
            EventBus.postAsync(RepeatTopicEvent(botMark, groupId))
        }
    }

    private fun loadCurrentTopic(botMark: String, groupId: String): String {
        return transaction {
            FlowStateEntity.find {
                (FlowStateTable.botMark eq botMark) and (FlowStateTable.groupId eq groupId)
            }.firstOrNull()?.currentTopic ?: ""
        }
    }

    private suspend fun persistAnalysisResult(botMark: String, groupId: String, result: FlowAnalysisResult) {
        withContext(Dispatchers.IO) {
            transaction {
                val flowState = FlowStateEntity.find {
                    (FlowStateTable.botMark eq botMark) and (FlowStateTable.groupId eq groupId)
                }.orderBy(FlowStateTable.lastProcessedAt to SortOrder.DESC).firstOrNull()

                flowState?.currentTopic = result.topicAnalysis.revisedTopic
            }
        }
    }

}