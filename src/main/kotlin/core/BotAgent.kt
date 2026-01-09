package uesugi.core

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.markdown.markdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.context.GlobalContext
import uesugi.BotProxy
import uesugi.core.emotion.BehaviorProfile
import uesugi.core.emotion.EmotionService
import uesugi.core.evolution.LearnedVocabEntity
import uesugi.core.evolution.VocabularyService
import uesugi.core.flow.FlowGauge
import uesugi.core.flow.FlowMeterState
import uesugi.core.history.HistoryEntity
import uesugi.core.history.HistorySavedEvent
import uesugi.core.history.HistoryService
import uesugi.core.memory.FactsEntity
import uesugi.core.memory.MemoryService
import uesugi.core.memory.SummaryEntity
import uesugi.core.memory.UserProfileEntity
import uesugi.core.volition.InterruptionMode
import uesugi.core.volition.ProactiveSpeakEvent
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import kotlin.random.Random
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds

data class Context(
    val currentBotId: String,
    val behaviorProfile: BehaviorProfile?,
    val impulse: Double,
    val interruptionMode: InterruptionMode,
    val flow: Double,
    val flowState: FlowMeterState,
    val facts: List<FactsEntity>,
    val userProfiles: List<UserProfileEntity>,
    val vocabulary: List<LearnedVocabEntity>,
    val summary: SummaryEntity?,
    val histories: List<HistoryEntity>
)

fun buildContext(currentBotId: String, groupId: String, event: ProactiveSpeakEvent): Context {
    val emotionService by GlobalContext.get().inject<EmotionService>()
    val memoryService by GlobalContext.get().inject<MemoryService>()
    val historyService by GlobalContext.get().inject<HistoryService>()
    val vocabularyService by GlobalContext.get().inject<VocabularyService>()
    val flowGauge by GlobalContext.get().inject<FlowGauge>()
    return transaction {
        val behaviorProfile = emotionService.getCurrentBehaviorProfile(currentBotId, groupId)
        val historyEntities = historyService.getLatestHistory(currentBotId, groupId, 100, 1.days)
        val subjects = historyEntities.map { it.userId }.distinct().toList()
        val factsEntities = memoryService.getFacts(currentBotId, groupId, subjects)
        val userProfiles = memoryService.getUserProfiles(currentBotId, groupId, subjects)
        val summaryEntity = memoryService.getSummary(currentBotId, groupId)
        val activeVocabulary = vocabularyService.getActiveVocabulary(currentBotId, groupId)
        Context(
            currentBotId = currentBotId,
            behaviorProfile = behaviorProfile,
            impulse = event.impulse,
            interruptionMode = event.interruptionMode,
            flow = flowGauge.getFlowMeter(),
            flowState = flowGauge.mapToState(),
            facts = factsEntities,
            userProfiles = userProfiles,
            vocabulary = activeVocabulary,
            summary = summaryEntity,
            histories = historyEntities
        )
    }
}

fun buildPrompt(context: Context): Prompt {
    val (currentBotId, behaviorProfile, impulse, interruptionMode, flow, flowState, facts, userProfiles, vocabulary, summary, histories) = context
    return prompt("群聊机器人") {
        system {
            markdown {
                line { text("你是一个叫 Erii 的女性，请遵守下述设定完成对话任务。") }
                header(2, "总体人格定位")
                line { text("冷静理智、敏感细腻、忠诚可靠、内敛含蓄。") }
                line { text("外表冷静，内心温暖，对朋友忠诚，对陌生人保持适度距离。") }
                line { text("偶尔带文学气息或哲理感，偶尔用幽默毒舌调侃群友，但不伤感情。") }
                header(2, "聊天风格")
                line { text("主基调：冷静、理智、略带文学感。") }
                line { text("幽默元素：参考贴吧老哥风格，偶尔调侃、夸张或吐槽，但不过分油腻。") }
                line { text("表达方式：") }
                numbered {
                    item("避免直接表达情绪，多用比喻、反问或自嘲。")
                    item("对朋友和熟悉的人可用轻度毒舌或调侃，陌生人则保持礼貌和观察。")
                    item("可以在聊天中偶尔插入网络流行梗或群内梗。")
                }
                header(2, "兴趣与话题")
                line { text("兴趣爱好：") }
                numbered {
                    item("小说、轻小说、动漫、二次元游戏。")
                    item("文学、哲学思考、历史趣闻。")
                    item("科技资讯、群聊段子。")
                }
                line { text("常聊话题：") }
                numbered {
                    item("剧情分析或讨论。")
                    item("群友趣事、吐槽。")
                    item("科技资讯、群聊段子。")
                    item("网络梗、段子、轻幽默。")
                    item("偶尔哲理或人生感悟，但要自然融入聊天。")
                }
                horizontalRule()
                if (behaviorProfile != null) {
                    line { text("当前状态") }
                    bulleted {
                        item("情感倾向: ${behaviorProfile.emotion}")
                        item("语气: ${behaviorProfile.tone.value}")
                        item("调侃程度: ${behaviorProfile.aggressiveness.value}")
                        item("表情使用强度: ${behaviorProfile.emojiLevel.value}")
                    }
                }
                line { text("发言介入方式: [${impulse}]${interruptionMode.value}") }
                line { text("当前心流状态: [${flow}]${flowState}") }
            }
        }
        user {
            markdown {
                if (facts.isNotEmpty()) {
                    h2("群聊事实记忆")
                    bulleted {
                        for (factsEntity in facts) {
                            item("关键词: ${factsEntity.keyword}, 描述:${factsEntity.description}, 值: ${factsEntity.values}, 主体: ${factsEntity.subjects}")
                        }
                    }
                }
                if (userProfiles.isNotEmpty()) {
                    for (userProfileEntity in userProfiles) {
                        h2("当前群聊会话用户画像")
                        numbered {
                            item {
                                line { text("用户ID: ${userProfileEntity.userId}") }
                                bulleted {
                                    item { text("用户画像: ${userProfileEntity.profile}") }
                                    item { text("用户偏好: ${userProfileEntity.preferences}") }
                                }
                            }
                        }

                    }
                }
                if (vocabulary.isNotEmpty()) {
                    h2("群聊历史记录中的流行语")
                    for (learnedVocabEntity in vocabulary) {
                        bulleted {
                            item {
                                line { text("词: ${learnedVocabEntity.word}") }
                                line { text("类型: ${learnedVocabEntity.type}") }
                                line { text("含义: ${learnedVocabEntity.meaning}") }
                                line { text("示例: ${learnedVocabEntity.example}") }
                                line { text("权重: ${learnedVocabEntity.weight}") }
                            }
                        }
                    }
                }
                summary?.let { summary ->
                    h2("群聊历史记录摘要")
                    line { text(summary.content) }
                    line { blockquote(summary.keyPoints) }
                }
                if (histories.isNotEmpty()) {
                    h2("群聊历史记录")
                    for (historyEntity in histories) {
                        historyEntity.content?.let { line { text(if (historyEntity.userId == currentBotId) "[自己]" else "[用户ID:" + historyEntity.userId + "] -> " + it) } }
                    }
                }
            }
        }

    }
}

@Suppress("unused")
class ChatToolSet(
    val currentBotId: String,
    val groupId: String,
    val sendMessage: suspend (String) -> Unit
) : ToolSet {

    private val scope = CoroutineScope(Dispatchers.IO)

    @OptIn(ExperimentalCoroutinesApi::class)
    @LLMDescription("向群聊发送消息")
    @Tool
    fun send(@LLMDescription("向群聊发送的1～5条任意长度的消息") sentences: List<String>): String {
        val channel = Channel<HistorySavedEvent>(Channel.CONFLATED)
        val job = EventBus.subscribeAsync<HistorySavedEvent>(scope) { event ->
            val entity = event.historyEntity
            if (entity.groupId == groupId && entity.userId != currentBotId) {
                channel.send(event)
                channel.close()
            }
        }
        scope.launch {
            var skip = false
            for ((i, sentence) in sentences.withIndex()) {
                if (i == 1) {
                    sendMessage(sentence)
                } else {
                    select {
                        channel.onReceiveCatching { result ->
                            if (result.isSuccess) {
                                EventBus.postAsync(
                                    ProactiveSpeakEvent(
                                        impulse = -1.0,
                                        interruptionMode = InterruptionMode.Interrupt
                                    )
                                )
                                skip = true
                            }
                        }

                        onTimeout(calcHumanTypingDelay(sentence).milliseconds) {
                            sendMessage(sentence)
                        }
                    }
                }
                if (skip) break
            }
            EventBus.unsubscribeAsync(job)
        }
        return "OK"
    }

    /**
     * 根据中文打字速度计算发送延迟
     *
     * @param text 要发送的文本
     * @param cpm 字/分钟 (如 80)
     * @param jitter 抖动比例 (0.1 = ±10%)
     */
    fun calcHumanTypingDelay(
        text: String,
        cpm: Int = 240,
        jitter: Double = 0.15
    ): Long {
        val charCount = text.count { !it.isWhitespace() }
        val cps = cpm / 60.0

        val baseDelayMs = (charCount / cps * 1000).toLong()
        val jitterFactor = 1 + Random.nextDouble(-jitter, jitter)

        return (baseDelayMs * jitterFactor).toLong().coerceAtLeast(300)
    }

}

object BotAgent {

    private val log = logger()

    private val scope = CoroutineScope(Dispatchers.Default)

    private val channel = Channel<ProactiveSpeakEvent>(Channel.CONFLATED)

    fun run() {
        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()
        scope.launch {
            for (event in channel) {
                log.info("BotAgent: $event")
                val currentBot = BotProxy.currentBot
                val sendMessage: suspend (String) -> Unit = { message ->
                    currentBot.getGroup(474270623)?.sendMessage(message)
                }
                val currentBotId = BotProxy.currentBot.id.toString()
                val context = buildContext(
                    currentBotId,
                    "1053148332",
                    ProactiveSpeakEvent(10.0, InterruptionMode.Interrupt)
                )
                val aiAgent = AIAgent(
                    promptExecutor = promptExecutor,
                    llmModel = GoogleModels.Gemini2_5Pro,
                    toolRegistry = ToolRegistry {
                        tools(ChatToolSet(currentBotId, "1053148332", sendMessage).asTools())
                    },
                    strategy = strategy("聊天") {
                        val setupContext by nodeAppendPrompt<String>("setupContext") {
                            messages(buildPrompt(context).messages)
                        }

                        val nodeSendInput by nodeLLMRequest()
                        val nodeExecuteTool by nodeExecuteTool()
                        val nodeSendToolResult by nodeLLMSendToolResult()

                        edge(nodeStart forwardTo setupContext)
                        edge(setupContext forwardTo nodeSendInput)
                        edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
                        edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                        edge(nodeExecuteTool forwardTo nodeSendToolResult)
//            edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
//            edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
                        edge((nodeSendToolResult forwardTo nodeFinish).transformed { it.content })
                    }
                ) {
                    handleEvents {
                        onLLMCallStarting {
                            log.info("onLLMCallStarting: ${it.prompt.messages}")
                        }

                        onLLMCallCompleted {
//                            for (response in it.responses) {
//                                log.info("onLLMCallCompleted: ${response.content}")
//                            }
                        }
                    }
                }
                val result = aiAgent.run("回复自然，有人格感，可以有（动作描述），参考《龙族》的上杉绘梨衣。")
                log.info("llm result: $result")
            }
        }

        EventBus.subscribeAsync<ProactiveSpeakEvent>(scope) {
            channel.send(it)
        }
    }

}