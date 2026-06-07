package uesugi.core.agent

import ai.koog.agents.core.agent.AIAgentService
import ai.koog.agents.core.agent.GraphAIAgent
import ai.koog.agents.core.agent.GraphAIAgentService
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.annotation.InternalAgentsApi
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.MessagePart
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uesugi.common.ChatToolSet
import uesugi.common.EventBus
import uesugi.common.LLMProviderChoice
import uesugi.common.event.*
import uesugi.common.toolkit.logger
import uesugi.common.toolkit.ref
import kotlin.reflect.full.functions
import kotlin.reflect.full.hasAnnotation
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime


object BotAgent {

    private val log = logger()

    private val fallbackEmoticons = listOf(
        // 困惑/懵逼
        "(×_×)", "(@_@)", "(；￣Д￣)", "(⊙_⊙;)", "(￣□￣;)", "(⊙＿⊙')",
        "(ﾟДﾟ；)", "(´･_･`)", "(・_・ヾ)", "(｡ŏ_ŏ)", "(◎_◎;)", "(°o°;)",
        "(o_O)", "(@[]@;;)", "(。_。)", "(；ω；)", "(´；ω；`)", "(・・；)",
        "( ꒪⌓꒪)", "(ㆆ_ㆆ)", "( ´△｀)", "( •́ ▾ •̀ )", "(ㅇㅅㅇ;)", "(๑•́ ₃ •̀๑)",

        // 尴尬/无奈
        "(>_<)", "(；・∀・)", "(._.)", "(´-ω-`)", "(￣▽￣*)ゞ", "( ;´Д｀)",
        "( -_-)", "(￣ヘ￣)", "(´Д｀)", "(´；д；`)", "(っ˘̩╭╮˘̩)っ", "(｡•́︿•̀｡)",
        "(｡ŏ﹏ŏ)", "(´-﹏-`；)", "( ´･･)ﾉ(._.`)",
        "(๑°o°๑)", "(｡♥‿♥｡)", "(◕‿◕✿)", "(｡◕‿◕｡)", "(✿◠‿◠)", "(◍•ᴗ•◍)",
        "(｡･ω･｡)ﾉ♡", "(⁄ ⁄•⁄ω⁄•⁄ ⁄)", "(⁎˃ᆺ˂)", "(๑˃̵ᴗ˂̵)و", "(｡･//ω//･｡)",
        "(´∩｡• ᵕ •｡∩`)", "(❁´◡`❁)", "(✿ ♡‿♡)", "(˵ ͡~ ͜ʖ ͡°˵)",

        // 惊讶/震惊
        "( ͡° ͜ʖ ͡°)", "( ⚆ _ ⚆ )", "(ﾉﾟ0ﾟ)ﾉ~", "(〇o〇；)", "Σ(°ロ°)", "(⊙_⊙)",
        "(º ﾛ º๑)", "(◎-◎；)", "(ʘᗩʘ')", "(◎_◎;)", "( ꒪ͧ-꒪ͧ)", "( ﾟдﾟ)つ", "｡ﾟ(ﾟ´Д｀ﾟ)ﾟ｡",

        // 流汗/心虚
        "(;;^_^;;)", "(；一_一)", "(^^ゞ", "(-_-;)・・・", "(^_^;)", "(°o°;)",
        "(；´∀｀)", "(￣◇￣;)", "(-_-メ)", "(；・∀・)", "(；´Д｀)", "(；・・)", "(；´_ゝ`)",

        // 睡觉/晕倒/去世
        "(=_=)", "(×_×)⌒☆", "(-_-) zzz", "(。-ω-)zzz", "(´～`ヾ)", "(￣o￣) zzZ",
        "(∪｡∪)｡｡｡zzz", "(￣д￣)ノ", "(￣□￣」)」", "( ´Д｀)y━・~~",

        // 祈祷/拜托
        "(人´▽｀)", "(ノ_＜)", "(つд⊂)", "(ﾉ´ｰ`)ﾉ", "(/ω＼)", "(╯▽╰ )",
        "(っ´ω｀)ﾉ(╥ω╥)", "( ´ ▽ ` )ﾉ",

        // 生气/暴躁
        "(¬_¬)", "(｀Δ´)ψ", "(｀ー´)", "(¬‿¬)", "(｀ε´)", "(╬ Ò ‸ Ó)",
        "(‡▼益▼)", "(¬､¬)", "( `ε´ )", "(눈_눈)", "(¬▂¬)", "(｀へ´)=3",

        // 更多创意
        "( ͡~ ͜ʖ ͡° )", "( ͡☉ ͜ʖ ͡☉)", "(✿╹◡╹)", "(っ˘ڡ˘ς)", "(づ｡◕‿‿◕｡)づ",
        "(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧", "(☞ﾟヮﾟ)☞", "☜(˚▽˚)☞", "¯\\_(ツ)_/¯", "(╯°□°）╯︵ ┻━┻",
        "┬─┬ ノ( ゜-゜ノ)", "( ͡• ͜ʖ ͡• )", "(ง'̀-'́)ง", "ᕕ( ᐛ )ᕗ", "(☉_☉)",
        "(•_•) ( •_•)>⌐■-■ (⌐■_■)", "ʕ•ᴥ•ʔ", "(☞ ͡° ͜ʖ ͡°)☞", "( ͡°ᴥ ͡° ʋ)",
        "( ﾟ▽ﾟ)/", "(⌐■_■)", "(☆▽☆)", "(♡˙︶˙♡)", "ヾ(•ω•`)o", "(っ＾▿＾)۶🍸",
        "(｡•̀ᴗ-)✧", "( ´ ∀ `)ノ～ ♡", "( ◡́.◡̀)", "( ^_^)/~~~", "(ﾉ´ з `)ノ",
        "( ◜‿◝ )♡", "(´｡• ᵕ •｡`) ♡", "( ˘ ³˘)♥︎", "(✯◡✯)", "( ´ ▽ ` ).｡ｏ♡",
        "¯\\_༼ ಥ ‿ ಥ ༽_/¯", "(ﾉ*ﾟｰﾟ)ﾉ", "(☆ω☆)", "(ノ^_^)ノ", "o(〃＾▽＾〃)o",
        "(๑˘▽˘๑)", "(*^ω^*)", "(◕‿◕✿)", "(｡♥‿♥｡)", "(✿◠‿◠)", "(◍•ᴗ•◍)❤",
        "(｡･ω･｡)ﾉ♡", "( ´ ∀ ` *)", "(◕ᴗ◕✿)", "(｡◕‿◕｡)", "(✿╹◡╹)",
        "(◍•ᴗ•◍)", "(´∩｡• ᵕ •｡∩`)", "(❁´◡`❁)", "(✿ ♡‿♡)", "(˵ ͡~ ͜ʖ ͡°˵)",
        "(⁄ ⁄•⁄ω⁄•⁄ ⁄)", "(⁎˃ᆺ˂)", "(๑˃̵ᴗ˂̵)و", "(◕‿◕✿)", "(｡◕‿◕｡)", "(✿◠‿◠)",
        "(◍•ᴗ•◍)", "(｡･ω･｡)ﾉ♡", "(｡♥‿♥｡)", "(◕‿◕✿)", "(｡◕‿◕｡)", "(✿◠‿◠)",
        "(◍•ᴗ•◍)❤", "(｡･ω･｡)ﾉ♡", "(｡♥‿♥｡)", "(◕‿◕✿)", "(｡◕‿◕｡)", "(✿◠‿◠)",
        "(◍•ᴗ•◍)", "(´∩｡• ᵕ •｡∩`)", "(❁´◡`❁)", "(✿ ♡‿♡)", "(˵ ͡~ ͜ʖ ͡°˵)",
        "(⁄ ⁄•⁄ω⁄•⁄ ⁄)", "(⁎˃ᆺ˂)", "(๑˃̵ᴗ˂̵)و"
    )

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

    private val chatToolNames by lazy {
        ChatToolSet::class.functions
            .filter { it.hasAnnotation<Tool>() }
            .map { it.name }
    }

    private suspend fun sendFallback(event: ProactiveSpeakEvent, context: Context) {
        val emoticon = fallbackEmoticons.random()
        log.info("LLM no call tool: {}", emoticon)
        buildChatToolSet(event, context).sendText(listOf(emoticon))
    }

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
        你是这个 QQ 群里的普通成员，正在跟大家一起聊天。
        像真人一样说话：自然、随意、有情绪，可以用短句、口语、颜文字。
        不要总结、不要分析、不要给建议，除非别人明确问你。
        可以只发几个字，也可以只发表情、表情包或图片，不想说话的时候可以不说话。
        你的目标是融入群聊，不是当一个"AI助手"。
    """.trimIndent()

    private val RETRY_HINT = """
        注意：你刚才没有使用任何工具，直接返回的文本不会出现在群聊中。
        请调用 sendText、sendMeme、sendImageByUrl、sendAtAndText 或 sendAtAll 等工具来回应。
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

    @OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class, InternalAgentsApi::class)
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

                        var calledAnyTool: Boolean
                        var calledChatTool = false

                        val strategy = strategy<String, String>("chat") {
                            val nodeSendInput by nodeLLMRequest()
                            val nodeExecuteTool by nodeExecuteTools()
                            val nodeSendToolResult by nodeLLMSendToolResults()

                            edge(nodeStart forwardTo nodeSendInput)
                            edge(nodeSendInput forwardTo nodeFinish onTextMessage { true })
                            edge(nodeSendInput forwardTo nodeExecuteTool onToolCalls { toolCall ->
                                calledAnyTool = true
                                val toolName = toolCall.tool.substringAfterLast(".")
                                if (toolName in chatToolNames) {
                                    calledChatTool = true
                                }
                                true
                            })
                            edge(
                                nodeExecuteTool forwardTo nodeFinish
                                        onCondition { results -> results.toolResults.all { it.resultObject == null } }
                                        transformed { "" }
                            )
                            edge(nodeExecuteTool forwardTo nodeSendToolResult)
                            edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCalls { toolCall ->
                                calledAnyTool = true
                                val toolName = toolCall.tool.substringAfterLast(".")
                                if (toolName in chatToolNames) {
                                    calledChatTool = true
                                }
                                true
                            })
                            edge(nodeSendToolResult forwardTo nodeFinish onTextMessage { true })
                            edge(
                                nodeSendToolResult forwardTo nodeFinish
                                        onCondition { msg ->
                                    msg.parts.any { it is MessagePart.Reasoning } &&
                                            msg.parts.none { it is MessagePart.Text || it is MessagePart.Tool.Call }
                                }
                                        transformed { msg ->
                                    msg.parts.filterIsInstance<MessagePart.Reasoning>()
                                        .joinToString("\n") { it.content.joinToString("\n") }
                                }
                            )
                        }

                        val context = buildContext(event)

                        val promptExecutor by ref<PromptExecutor>()

                        val aiAgent = AIAgentService(
                            promptExecutor = promptExecutor,
                            agentConfig = AIAgentConfig(
                                prompt = buildPrompt(context),
                                model = LLMProviderChoice.Pro,
                                maxAgentIterations = 50,
                            ),
                            strategy = strategy
                        ) { handleEvents(event) }


                        val roundAgentRun = (::agentRun).curry()(aiAgent)(context)

                        suspend fun runWithRetry(targetEvent: ProactiveSpeakEvent) {
                            val baseInput = targetEvent.input ?: DEFAULT_INPUT
                            repeat(3) { attempt ->
                                calledAnyTool = false
                                calledChatTool = false
                                val runInput = if (attempt == 0) baseInput else "$baseInput\n\n$RETRY_HINT"
                                log.info(
                                    "BotAgent: Agent run attempt {}/3 for group={}",
                                    attempt + 1,
                                    targetEvent.groupId
                                )
                                roundAgentRun(targetEvent.copy(input = runInput))
                                if (calledAnyTool) {
                                    if (calledChatTool) {
                                        log.info("BotAgent: Chat tool called on attempt {}, no retry", attempt + 1)
                                    } else {
                                        log.info("BotAgent: Non-chat tool called on attempt {}, no retry", attempt + 1)
                                    }
                                    return
                                }
                                if (attempt < 2) {
                                    log.warn("BotAgent: No tool called on attempt {}/3, will retry", attempt + 1)
                                }
                            }
                            log.warn("BotAgent: No tool called after 3 attempts, will fallback")
                        }

                        runWithRetry(event)
                        if (!calledChatTool) {
                            log.warn("BotAgent: No chat tool called for event={}, sending fallback", event.groupId)
                            sendFallback(event, context)
                        }

                        while (true) {
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

                            runWithRetry(newEvent)
                            if (!calledChatTool) {
                                sendFallback(newEvent, context)
                            }
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

    @OptIn(ExperimentalTime::class)
    private suspend fun agentRun(
        aiAgent: GraphAIAgentService<String, String>,
        context: Context,
        event: ProactiveSpeakEvent
    ) {
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
                agentInput = event.input ?: DEFAULT_INPUT,
                agentConfig = AIAgentConfig(
                    prompt = buildPrompt(context),
                    model = LLMProviderChoice.Pro,
                    maxAgentIterations = 50,
                ),
                additionalToolRegistry = with(buildToolEnv(event, context)) { buildToolRegistry() },
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

    private fun GraphAIAgent.FeatureContext.handleEvents(event: ProactiveSpeakEvent) {
        handleEvents {
            onLLMCallStarting {
                if (log.isDebugEnabled) {
                    val info = buildString {
                        appendLine()
                        for (message in it.prompt.messages) {
                            append("${message.role.name}:")
                            appendLine()
                            append(message.textContent())
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
                        val response = it.response
                        if (response != null) {
                            append("${response.role.name}:")
                            appendLine()
                            append(response.textContent())
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

    fun <A, B, C, R> (suspend (A, B, C) -> R).curry(): suspend (A) -> suspend (B) -> suspend (C) -> R =
        { a ->
            { b ->
                { c ->
                    this(a, b, c)
                }
            }
        }
}