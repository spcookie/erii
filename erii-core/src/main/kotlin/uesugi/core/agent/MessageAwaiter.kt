package uesugi.core.agent

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import uesugi.BotManage
import uesugi.common.*
import uesugi.core.message.history.HistorySavedEvent
import uesugi.core.plugin.MetaImpl
import uesugi.core.route.MetaToolSetRegister
import uesugi.spi.MetaToolSet.Companion.meta
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MessageAwaiter(val context: Context) : AutoCloseable, CoroutineScope {

    companion object {
        private val log = KotlinLogging.logger {}
    }

    private val historyChannel = Channel<ProactiveSpeakEvent>()
    private lateinit var historyJob: Job

    private val relevanceChannel = Channel<RelevanceType>(Channel.CONFLATED)

    private val chatChannel = Channel<ProactiveSpeakEvent>()
    private lateinit var chatUrgentJob: Job

    private val continueChannel = Channel<ProactiveSpeakEvent>()

    private enum class RelevanceType {
        Message, Continue
    }

    val onReceiveMessageContinue
        get() = historyChannel.onReceiveCatching

    val onChatUrgentContinue
        get() = chatChannel.onReceiveCatching

    fun fare() {
        launch {
            relevanceChannel.receiveAsFlow()
                .collect { type ->
                    try {
                        if (isRelevanceContinue(context.histories(), context.currentBotId)) {
                            when (type) {
                                RelevanceType.Message -> {
                                    historyChannel.send(speak(context.currentBotId, context.groupId))
                                }

                                RelevanceType.Continue -> {
                                    continueChannel.send(speak(context.currentBotId, context.groupId))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        log.warn(e) { "Failed to determine relevance continue" }
                    }
                }
        }

        historyJob = EventBus.subscribeAsync<HistorySavedEvent>(this) { event ->
            val record = event.historyRecord
            if (record.groupId == context.groupId && record.userId != context.currentBotId) {
                if (!event.isAtBot) {
                    relevanceChannel.send(RelevanceType.Message)
                }
            }
        }

        chatUrgentJob = EventBus.subscribeAsync<ChatUrgentEvent>(this) { event ->
            val speak = event.urgent
            if (speak.groupId == context.groupId && speak.botId == context.currentBotId) {
                chatChannel.send(speak(context.currentBotId, context.groupId, speak.senderId, speak.input))
            }
        }

    }

    @OptIn(ExperimentalUuidApi::class)
    private fun speak(
        botId: String,
        groupId: String,
        senderId: String? = null,
        input: String? = null
    ): ProactiveSpeakEvent {
        val echo = Uuid.random().toHexString()
        val metaToolSets = MetaToolSetRegister.getAllToolSets()
            .map { toolSetApply ->
                toolSetApply().apply {
                    meta = MetaImpl(
                        botId = botId,
                        groupId = groupId,
                        senderId = senderId,
                        roledBot = BotManage.getBot(botId),
                        input = input,
                        echo = echo
                    )
                }
            }
        return ProactiveSpeakEvent(
            botId = botId,
            _groupId = groupId,
            senderId = senderId,
            webSearch = true,
            interruptionMode = InterruptionMode.Interrupt,
            input = input,
            echo = echo,
            toolSetBuilder = { metaToolSets }
        )
    }

    override fun close() {
        if (::historyJob.isInitialized) {
            historyJob.cancel()
        }
        if (::chatUrgentJob.isInitialized) {
            chatUrgentJob.cancel()
        }

        cancel()

        historyChannel.close()
        chatChannel.close()
        continueChannel.close()
        relevanceChannel.close()
    }

    override val coroutineContext: CoroutineContext
        get() = SupervisorJob() +
                CoroutineName("message-awaiter-${context.currentBotId}-${context.groupId}") +
                Dispatchers.Default +
                CoroutineExceptionHandler { _, error ->
                    log.info { "MessageAwaiter coroutine exception: $error" }
                }

    @OptIn(ExperimentalTime::class)
    private suspend fun isRelevanceContinue(histories: List<HistoryRecord>, currentBotId: String): Boolean {
        val promptExecutor by ref<PromptExecutor>()

        val prompt = prompt("relevance-continue") {
            system(
                """
            你是一个群聊行为判断器。
            
            任务：
            根据最近的聊天记录，判断机器人在当前群聊中是否应该继续发言。群聊机器人名叫 ${BotManage.getBot(currentBotId).role.name}。
            
            输入：
            一段按时间顺序排列的聊天记录。
            
            重要规则：
            - 机器人最后一条消息之后的消息称为“观察窗口”
            - 判断重点是“最新一条消息”是否仍然在和机器人对话
            - 历史消息只用于理解上下文，不应该直接触发机器人继续发言
            
            判定逻辑：
            
            1. 如果机器人最后一条消息之后 **没有任何新消息**
            → 输出 SILENT
            
            2. 如果最新消息明显是在 **和机器人说话或向机器人提问**
            例如：
            - 提到机器人名字
            - 问机器人问题
            - 要求机器人做事情
            - 继续机器人刚才的话题
            → 输出 CONTINUE
            
            3. 如果最新消息是在 **回应机器人刚才的话题**
            例如：
            - 回答机器人问题
            - 提供机器人需要的信息
            - 继续讨论机器人提出的话题
            → 输出 CONTINUE
            
            4. 如果最新消息只是 **感谢 / 结束 / 表情 / 已解决**
            例如：
            - 谢谢
            - 好的
            - 明白了
            - 已解决
            → 输出 SILENT
            
            5. 如果最新消息开始了 **新的群聊话题，与机器人无关**
            例如：
            - 群友互相聊天
            - 讨论其他事情
            - 闲聊
            → 输出 SILENT
            
            6. 默认规则：
            如果无法确定最新消息是否需要机器人参与
            → 输出 SILENT
            最新消息是在问机器人原因
            → CONTINUE
            问题已结束
            → SILENT
            如果机器人最后发言后
            出现 >=3 条连续用户消息
            且没有再提到机器人
            → SILENT
            
            最终输出规则：
            
            只能输出一个词：
            CONTINUE
            或
            SILENT
            
            不能输出任何解释或其他内容。
            """.trimIndent()
            )
            user {
                markdown {
                    buildHistoriesPrompt(histories, currentBotId)
                }
            }
        }

        val responses = promptExecutor.execute(prompt, LLMModelsChoice.Flash)
        val content = responses.filterIsInstance<Message.Assistant>().firstOrNull()?.content
        log.info { "Relevance continue determine LLM response: $content" }
        return content != null && content.contains("CONTINUE")
    }

}
