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
import ai.koog.prompt.dsl.Prompt
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.markdown.markdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import uesugi.BotProxy
import uesugi.core.emotion.EmotionService
import uesugi.core.evolution.VocabularyService
import uesugi.core.flow.FlowGauge
import uesugi.core.history.HistoryService
import uesugi.core.memory.MemoryService
import uesugi.core.volition.InterruptionMode
import uesugi.core.volition.ProactiveSpeakEvent
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import kotlin.random.Random

fun botPrompt(currentBotId: String, groupId: String, event: ProactiveSpeakEvent): Prompt {
    val emotionService by GlobalContext.get().inject<EmotionService>()
    val memoryService by GlobalContext.get().inject<MemoryService>()
    val historyService by GlobalContext.get().inject<HistoryService>()
    val vocabularyService by GlobalContext.get().inject<VocabularyService>()

    val flowGauge by GlobalContext.get().inject<FlowGauge>()

    val behaviorProfile = emotionService.getCurrentBehaviorProfile(currentBotId, groupId)
    val historyEntities = historyService.getLatestHistory(groupId, groupId, 200)
    val subjects = historyEntities.map { it.userId }.distinct().toList()
    val factsEntities = memoryService.getFacts(currentBotId, groupId, subjects)
    val userProfiles = memoryService.getUserProfiles(currentBotId, groupId, subjects)
    val summaryEntity = memoryService.getSummary(currentBotId, groupId)
    val activeVocabulary = vocabularyService.getActiveVocabulary(currentBotId, groupId)

    return prompt("群聊机器人") {
        system {
            markdown {
                header(2, "总体人格定位")
                line { text("女性，参考《龙族》的上杉绘梨衣。") }
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
                        item("emotional tendencies: ${behaviorProfile.emotion}")
                        item("tone: ${behaviorProfile.tone.value}")
                        item("aggressiveness: ${behaviorProfile.aggressiveness.value}")
                        item("verbosity: ${behaviorProfile.verbosity}")
                        item("emojiLevel: ${behaviorProfile.emojiLevel}")
                    }
                }
                horizontalRule()
                line { text("发言模式: ${event.interruptionMode.value}") }
                line { text("发言句子长度: ${flowGauge.mapToState().name}") }
            }
        }
        user {
            markdown {
                if (factsEntities.isNotEmpty()) {
                    h2("群聊事实记忆")
                    bulleted {
                        for (factsEntity in factsEntities) {
                            item("values: ${factsEntity.keyword}, description:${factsEntity.description}, values: ${factsEntity.values}, subjects: ${factsEntity.subjects}")
                        }
                    }
                }
                summaryEntity?.let { summary ->
                    h2("群聊历史记录摘要")
                    line { text(summary.content) }
                    line { blockquote(summary.keyPoints) }
                }
                if (userProfiles.isNotEmpty()) {
                    for (userProfileEntity in userProfiles) {
                        h2("当前群聊会话用户画像")
                        numbered {
                            item {
                                line { text("userId: ${userProfileEntity.userId}") }
                                bulleted {
                                    item { text("profile: ${userProfileEntity.profile}") }
                                    item { text("preferences: ${userProfileEntity.preferences}") }
                                }
                            }
                        }

                    }
                }
                if (activeVocabulary.isNotEmpty()) {
                    for (learnedVocabEntity in activeVocabulary) {
                        h2("群聊历史记录中的流行语")
                        bulleted {
                            item {
                                line { text("word: ${learnedVocabEntity.word}") }
                                line { text("type: ${learnedVocabEntity.type}") }
                                line { text("meaning: ${learnedVocabEntity.meaning}") }
                                line { text("example: ${learnedVocabEntity.example}") }
                                line { text("weight: ${learnedVocabEntity.weight}") }
                            }
                        }
                    }
                }
            }
        }
        for (historyEntity in historyEntities) {
            if (historyEntity.userId == currentBotId) {
                historyEntity.content?.let { assistant { text(it) } }
            } else {
                historyEntity.content?.let { user { text(historyEntity.userId + ": " + it) } }
            }
        }
    }
}

class ChatToolSet(val sendMessage: suspend (String) -> Unit) : ToolSet {

    private val scope = CoroutineScope(Dispatchers.IO)

    @LLMDescription("向群聊发送消息")
    @Tool
    fun send(@LLMDescription("向群聊发送的多条句子") sentences: List<String>): String {
        scope.launch {
            for (sentence in sentences) {
                delay(calcHumanTypingDelay(sentence))
                sendMessage(sentence)
            }
        }
        return "Ok"
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
        cpm: Int = 160,
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
                val aiAgent = AIAgent(
                    promptExecutor = promptExecutor,
                    llmModel = GoogleModels.Gemini2_5Pro,
                    toolRegistry = ToolRegistry {
                        tools(ChatToolSet(sendMessage).asTools())
                    },
                    strategy = strategy("聊天") {
                        val setupContext by nodeAppendPrompt<String>("setupContext") {
                            messages(
                                botPrompt(
                                    BotProxy.currentBot.id.toString(),
                                    "1053148332",
                                    ProactiveSpeakEvent(10.0, InterruptionMode.Interrupt)
                                ).messages
                            )
                        }

                        val nodeSendInput by nodeLLMRequest()
                        val nodeExecuteTool by nodeExecuteTool()
                        val nodeSendToolResult by nodeLLMSendToolResult()

                        edge(nodeStart forwardTo setupContext)
                        edge(setupContext forwardTo nodeSendInput)
                        edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                        edge(nodeExecuteTool forwardTo nodeSendToolResult)
                        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
                        edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
                        edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
                    }
                )
                aiAgent.run("参与聊天")
            }
        }

        EventBus.subscribeAsync(ProactiveSpeakEvent::class, scope) {
            channel.send(it)
        }
    }

}