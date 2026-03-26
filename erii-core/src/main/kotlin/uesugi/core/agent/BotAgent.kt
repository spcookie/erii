package uesugi.core.agent

import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonNull
import uesugi.BotManage
import uesugi.common.*
import uesugi.core.component.WebSearchTool
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime


object BotAgent {

    private val log = logger()

    private val scope = CoroutineScope(
        SupervisorJob()
                + Dispatchers.Default
                + CoroutineName("BotAgent")
                + CoroutineExceptionHandler { _, e ->
            log.error("BotAgent error", e)
        })

    private data class BotGroupKey(val botId: String, val groupId: String)
    private data class BotGroupState(
        val flag: ProactiveSpeakFeature?,
        val cancel: (() -> Unit)?
    )

    private val channels = mutableMapOf<BotGroupKey, Channel<ProactiveSpeakEvent?>>()
    private val states = mutableMapOf<BotGroupKey, BotGroupState>()
    private val channelsLock = Mutex()
    private val statesLock = Mutex()

    private suspend fun getChannel(botId: String, groupId: String): Channel<ProactiveSpeakEvent?> {
        val key = BotGroupKey(botId, groupId)
        return channelsLock.withLock {
            channels.getOrPut(key) {
                Channel<ProactiveSpeakEvent?>().also { channel ->
                    scope.launch {
                        processChannel(key, channel)
                    }
                    channel.send(null)
                }
            }
        }
    }

    private val DEFAULT_INPUT = """
                你是在聊天，不是在写答案，不是在总结。
                """.trimIndent()

    fun run() {
        EventBus.subscribeAsync<ProactiveSpeakEvent>(scope) {
            val channel = getChannel(it.botId, it.groupId)
            val key = BotGroupKey(it.botId, it.groupId)

            val result = channel.trySend(it)

            if (result.isFailure) {
                val state = statesLock.withLock { states[key] }
                if (it.feature has PSFeature.CHAT_URGENT) {
                    log.info("BotAgent: Chat urgent, {}", it)
                    EventBus.postAsync(ChatUrgentEvent(it))
                } else if (it.feature has PSFeature.GRAB) {
                    if (state?.flag has PSFeature.IGNORE_INTERRUPT) {
                        if (it.feature has PSFeature.FALLBACK) {
                            log.warn("BotAgent: Reject grab and dispatch fallback, {}", it)
                            EventBus.postSync(
                                AgentCallFallbackEvent(
                                    it.botId,
                                    it.groupId,
                                    it.echo
                                )
                            )
                        } else {
                            log.warn("BotAgent: Reject grab, {}", it)
                            EventBus.postSync(
                                AgentCallRejectEvent(
                                    it.botId,
                                    it.groupId,
                                    it.echo
                                )
                            )
                        }
                    } else {
                        state?.cancel?.invoke()
                        channel.send(it)
                    }
                } else if (it.feature has PSFeature.FALLBACK) {
                    log.warn("BotAgent: Fallback, {}", it)
                    EventBus.postSync(
                        AgentCallFallbackEvent(
                            it.botId,
                            it.groupId,
                            it.echo
                        )
                    )
                } else {
                    channel.trySend(it)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)
    private suspend fun processChannel(key: BotGroupKey, channel: Channel<ProactiveSpeakEvent?>) {
        for (event in channel) {
            if (event == null) continue
            log.info("Bot agent received event: $event")
            try {
                val job = scope.launch {
                    var error: Throwable? = null
                    try {
                        EventBus.postSync(
                            AgentCallStartEvent(
                                event.botId,
                                event.groupId,
                                event.echo
                            )
                        )

                        statesLock.withLock {
                            states[key] = BotGroupState(event.feature, null)
                        }

                        val currentBot = BotManage.getBot(event.botId)
                        val groupId = event.groupId
                        val bot = currentBot.refBot

                        val context = buildContext(event)

                        val chatToolSet = QQChatToolSet(
                            bot = bot,
                            groupId = groupId.toLong(),
                            context = context
                        )

                        var noCallTool = false

                        val strategy = strategy("chat") {
                            val nodeSendInput by nodeLLMRequest()
                            val nodeExecuteTool by nodeExecuteTool()
                            val nodeSendToolResult by nodeLLMSendToolResult()

                            edge(nodeStart forwardTo nodeSendInput)
                            edge(nodeSendInput forwardTo nodeFinish onAssistantMessage {
                                noCallTool = true
                                true
                            })
                            edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                            edge(nodeExecuteTool forwardTo nodeSendToolResult onCondition {
                                val result = it.result ?: return@onCondition false
                                result !is JsonNull
                            })
                            edge(nodeExecuteTool forwardTo nodeFinish onCondition {
                                val result = it.result ?: return@onCondition true
                                result is JsonNull
                            } transformed { it.content })
                            edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
                            edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
                        }

                        val promptExecutor by ref<PromptExecutor>()

                        val aiAgent = AIAgentService(
                            promptExecutor = promptExecutor,
                            agentConfig = AIAgentConfig(
                                prompt = buildPrompt(context),
                                model = LLMModelsChoice.Pro,
                                maxAgentIterations = 20,
                            ),
                            strategy = strategy
                        ) {
                            handleEvents {
                                onLLMCallStarting {
                                    if (log.isDebugEnabled) {
                                        val info = buildString {
                                            appendLine()
                                            for (message in it.prompt.messages) {
                                                append("${message.role.name}:")
                                                appendLine()
                                                append(message.content)
                                                appendLine()
                                            }
                                        }
                                        log.debug("Bot agent onLLMCallStarting: {}", info)
                                    }
                                }

                                onLLMCallCompleted {
                                    if (log.isDebugEnabled) {
                                        val info = buildString {
                                            appendLine()
                                            for (message in it.responses) {
                                                append("${message.role.name}:")
                                                appendLine()
                                                append(message.content)
                                                appendLine()
                                            }
                                        }
                                        log.debug("Bot agent onLLMCallCompleted: {}", info)
                                    }
                                }

                                onToolCallStarting {
                                    EventBus.postAsync(
                                        AgentToolCallStartEvent(
                                            event.botId,
                                            event.groupId,
                                            event.echo,
                                            it.toolName,
                                            it.toolArgs
                                        )
                                    )
                                }

                                onToolCallCompleted {
                                    EventBus.postAsync(
                                        AgentToolCallCompleteEvent(
                                            event.botId,
                                            event.groupId,
                                            event.echo,
                                            it.toolName,
                                            it.toolArgs,
                                            it.toolResult,
                                            null
                                        )
                                    )
                                }

                                onToolCallFailed {
                                    EventBus.postAsync(
                                        AgentToolCallCompleteEvent(
                                            event.botId,
                                            event.groupId,
                                            event.echo,
                                            it.toolName,
                                            it.toolArgs,
                                            null,
                                            it.message
                                        )
                                    )
                                }
                            }
                        }

                        suspend fun agentRun(input: String, toolEnv: ToolEnv) {
                            EventBus.postSync(
                                AgentRunStartEvent(
                                    event.botId,
                                    event.groupId,
                                    event.echo
                                )
                            )
                            var error: Exception? = null
                            try {
                                val text = aiAgent.createAgentAndRun(
                                    agentInput = input,
                                    agentConfig = AIAgentConfig(
                                        prompt = buildPrompt(context),
                                        model = LLMModelsChoice.Pro,
                                        maxAgentIterations = 20,
                                    ),
                                    additionalToolRegistry = with(toolEnv) { buildToolRegistry() },
                                )
                                log.info("Bot agent run result: {}", text)
                            } catch (e: Exception) {
                                error = e
                                throw e
                            } finally {
                                EventBus.postSync(
                                    AgentRunCompleteEvent(
                                        error,
                                        event.botId,
                                        event.groupId,
                                        event.echo
                                    )
                                )
                            }
                        }

                        agentRun(
                            event.input ?: DEFAULT_INPUT,
                            ToolEnv(chatToolSet, event.webSearch, event.toolSetBuilder)
                        )

                        while (true) {
                            if (noCallTool) {
                                noCallTool = false
                                val emoticon = listOf(
                                    "(×_×)",
                                    "(@_@)",
                                    "(；￣Д￣)",
                                    "(⊙_⊙;)",
                                    "(>_<)",
                                    "(￣□￣;)",
                                    "(╯°□°）╯︵ ┻━┻",
                                    "(⊙＿⊙')",
                                    "(；・∀・)",
                                    "(._.)"
                                )
                                log.info("LLM no call tool: {}", emoticon)
                                chatToolSet.sendText(emoticon.random())
                            }

                            val newEvent = MessageAwaiter(context)
                                .apply {
                                    fare()
                                }.use { awaiter ->
                                    select {
                                        awaiter.onChatUrgentContinue { it.getOrNull() }
                                        awaiter.onReceiveMessageContinue { it.getOrNull() }
                                        onTimeout(5.minutes) { null }
                                    }
                                }

                            if (newEvent == null) {
                                break
                            }

                            agentRun(
                                DEFAULT_INPUT,
                                ToolEnv(chatToolSet, newEvent.webSearch, newEvent.toolSetBuilder)
                            )
                        }
                    } catch (e: Exception) {
                        error = e
                        throw e
                    } finally {
                        EventBus.postSync(
                            AgentCallCompleteEvent(
                                error,
                                event.botId,
                                event.groupId,
                                event.echo
                            )
                        )
                    }
                }

                val cancel = { job.cancel() }

                statesLock.withLock {
                    states[key] = states[key]?.copy(cancel = cancel) ?: BotGroupState(null, cancel)
                }

                job.join()
            } catch (e: CancellationException) {
                log.warn("Bot agent sub job cancelled", e)
            } catch (e: Exception) {
                log.error("Bot agent sub job error", e)
            }
        }
    }

    private data class ToolEnv(
        val chatToolSet: ChatToolSet,
        val webSearch: Boolean,
        val toolSetBuilder: ((ChatToolSet) -> List<ToolSet>)?
    )

    context(env: ToolEnv)
    private fun baseTools() = buildList {
        addAll(env.chatToolSet.asTools())
        addAll(SilentToolSet.asTools())
    }

    context(env: ToolEnv)
    private fun webTools() =
        if (env.webSearch) WebSearchTool.asTools() else emptyList()

    context(env: ToolEnv)
    private fun extraTools() =
        env.toolSetBuilder?.invoke(env.chatToolSet)
            ?.flatMap { it.asTools() }
            ?: emptyList()

    context(env: ToolEnv)
    private fun buildToolRegistry(): ToolRegistry =
        ToolRegistry {
            tools(baseTools())
            tools(webTools())
            tools(extraTools())
        }

}