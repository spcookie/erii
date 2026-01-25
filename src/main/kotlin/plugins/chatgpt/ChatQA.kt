package uesugi.plugins.chatgpt

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.filterTextOnly
import ai.koog.prompt.streaming.toMessageResponses
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.jsonNull
import net.mamoe.mirai.contact.Group
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.context.GlobalContext
import plugins.Plugin
import uesugi.BotManage
import uesugi.config.LLMModelsChoice
import uesugi.core.RouteCallEvent
import uesugi.core.RouteRule
import uesugi.core.buildMetadataPrompt
import uesugi.core.history.HistoryService
import uesugi.toolkit.EventBus
import uesugi.toolkit.WebSearchTool
import uesugi.toolkit.calcHumanTypingDelay
import uesugi.toolkit.logger
import kotlin.time.Duration.Companion.days

class ChatQA : Plugin {

    private val logger = logger()

    private val scope = CoroutineScope(
        SupervisorJob()
                + Dispatchers.Default
                + CoroutineName("ChatQA")
                + CoroutineExceptionHandler { _, exception ->
            logger.error("ChatQA Coroutine exception", exception)
        })

    private lateinit var job: Job

    override fun onLoad() {
        val historyService by GlobalContext.get().inject<HistoryService>()
        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()

        job = EventBus.subscribeAsync<RouteCallEvent>(scope) { event ->
            if (event.hit == RouteRule.DIRECT_QA) {
                val ctx = withContext(Dispatchers.IO) {
                    transaction {
                        val history = historyService.getLatestHistory(event.botId, event.groupId, 10, 1.days)
                        buildString {
                            for (entity in history) {
                                appendLine("${entity.nick}: ${entity.content}")
                            }
                        }
                    }
                }

                val bot = BotManage.getBot(event.botId).refBot
                val group = bot.getGroup(event.groupId.toLong())!!

                fun toolValid(ret: ReceivedToolResult, require: Boolean): Boolean {
                    return try {
                        val result = ret.result ?: return require
                        result.jsonNull
                        require
                    } catch (_: Exception) {
                        !require
                    }
                }

                suspend fun AIAgentLLMWriteSession.requestLLM(group: Group): List<Message.Response> {
                    val flow = requestLLMStreaming()
                    val buffer = StringBuilder()
                    var skip = true
                    val responses = buildList {
                        flow.onEach { add(it) }
                            .filterTextOnly()
                            .collect { chunk ->
                                if (chunk.isEmpty()) {
                                    return@collect
                                }
                                buffer.append(chunk)
                                val parts = buffer.toString()
                                    .lineSequence()
                                    .map { it.trim() }
                                    .filter { it.isNotEmpty() }
                                    .toList()
                                parts.dropLast(1).forEach { msg ->
                                    msg.trim()
                                        .takeIf { it.isNotEmpty() }
                                        ?.let {
                                            if (!skip) {
                                                delay(calcHumanTypingDelay(it))
                                            }
                                            group.sendMessage(it)
                                        }
                                    if (skip) {
                                        skip = false
                                    }
                                }
                                buffer.clear()
                                buffer.append(parts.last())
                            }
                    }.toMessageResponses()
                    buffer.toString().trim().takeIf { it.isNotEmpty() }?.let {
                        group.sendMessage(it)
                    }
                    return responses
                }

                val prompt = prompt("QA") {
                    system {
                        text(
                            """
                            你是一个专业的问答助手，只回答用户提出的问题的核心内容，保持简洁明了。  
    
                            要求：
                            1. 不要输出 Markdown 或列表符号。
                            2. 直接输出文本答案，不要多余前缀、标题或注释。
                            3. 如果问题可以简短回答就直接回答，不要冗长。
                            4. 对于复杂问题，也尽量保持回答直截了当、可理解。
                            5. 回答的句子末尾需要换行。
                        """.trimIndent()
                        )
                        markdown {
                            buildMetadataPrompt()
                        }
                    }

                    user(
                        """
                        当前上下文：
                        $ctx
                    """.trimIndent()
                    )
                }

                val agent = AIAgent(
                    promptExecutor = promptExecutor,
                    agentConfig = AIAgentConfig(
                        prompt = prompt,
                        model = LLMModelsChoice.Pro,
                        maxAgentIterations = 10
                    ),
                    toolRegistry = ToolRegistry {
                        tools(WebSearchTool.asTools())
                    },
                    strategy = strategy("QA") {
                        val nodeSendInput by node<String, Message.Response> { input ->
                            llm.writeSession {
                                appendPrompt {
                                    user {
                                        text(input)
                                    }
                                }
                                val responses = requestLLM(group)
                                responses[0]
                            }
                        }
                        val nodeExecuteTool by nodeExecuteTool()
                        val nodeSendToolResult by node<ReceivedToolResult, Message.Response> { result ->
                            llm.writeSession {
                                appendPrompt {
                                    tool {
                                        result(result)
                                    }
                                }
                                val responses = requestLLM(group)
                                responses[0]
                            }
                        }

                        edge(nodeStart forwardTo nodeSendInput)
                        edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
                        edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
                        edge(
                            (nodeExecuteTool forwardTo nodeSendToolResult)
                                    onCondition { toolValid(it, false) }
                        )
                        edge(
                            (nodeExecuteTool forwardTo nodeFinish)
                                    onCondition { toolValid(it, true) }
                                    transformed { it.content }
                        )
                        edge(nodeSendToolResult forwardTo nodeExecuteTool onToolCall { true })
                        edge(nodeSendToolResult forwardTo nodeFinish onAssistantMessage { true })
                    }
                )

                agent.run(
                    """
                        用户问题：
                        ${event.input}

                        请给出直接的答案
                """.trimIndent()
                )

            }
        }
    }

    override fun onUnload() {
        if (::job.isInitialized) {
            EventBus.unsubscribeAsync(job)
        }
    }

}