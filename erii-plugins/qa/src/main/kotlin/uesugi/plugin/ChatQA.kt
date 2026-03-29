package uesugi.plugin

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.agent.entity.AIAgentGraphStrategy
import ai.koog.agents.core.agent.session.AIAgentLLMWriteSession
import ai.koog.agents.core.dsl.builder.node
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.environment.ReceivedToolResult
import ai.koog.agents.core.environment.result
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.message.Message
import ai.koog.prompt.streaming.filterTextOnly
import ai.koog.prompt.streaming.toMessageResponses
import ai.koog.serialization.kotlinx.toKotlinxJsonElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.jsonNull
import net.mamoe.mirai.contact.Group
import org.pf4j.Extension
import uesugi.common.LLMModelsChoice
import uesugi.common.calcHumanTypingDelay
import uesugi.core.component.WebSearchTool
import uesugi.spi.*
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

@PluginDefinition
class ChatQA : AgentPlugin()

@Extension
class ChatQAExtension : RouteExtension<ChatQA> {

    @OptIn(ExperimentalTime::class)
    override fun onLoad(context: PluginContext) {
        fun toolValid(ret: ReceivedToolResult, require: Boolean): Boolean {
            return try {
                val result = ret.result ?: return require
                result.toKotlinxJsonElement().jsonNull
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

        fun getStrategy(group: Group): AIAgentGraphStrategy<String, String> {
            return strategy("QA") {
                val nodeSendInput by node<String, Message.Response> { input ->
                    llm.writeSession {
                        appendPrompt {
                            user {
                                text(input)
                            }
                        }
                        val responses = requestLLM(group)
                        responses.first { it !is Message.Reasoning }
                    }
                }

                val nodeExecuteTool by node<Message.Tool.Call, ReceivedToolResult> { toolCall ->
                    llm.writeSession {
                        appendPrompt {
                            tool {
                                call(toolCall)
                            }
                        }
                    }
                    environment.executeTool(toolCall)
                }

                val nodeSendToolResult by node<ReceivedToolResult, Message.Response> { toolResult ->
                    llm.writeSession {
                        appendPrompt {
                            tool {
                                result(toolResult)
                            }
                        }
                        val responses = requestLLM(group)
                        responses.first { it !is Message.Reasoning }
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
        }

        context.chain { meta ->
            val history = database.getLatestHistory(
                meta.botId,
                meta.groupId,
                10,
                1.days
            )
            val ctx = buildString {
                for (entity in history) {
                    appendLine("${entity.nick}: ${entity.content}")
                }
            }
            val group = meta.getGroup()

            val prompt = prompt("QA") {
                system {
                    text(
                        """
                            你是一个专业的问答助手，只回答用户提出的问题的核心内容，保持简洁明了。  
    
                            要求：
                            1. 不要输出 Markdown 格式。
                            2. 直接输出文本答案，不要多余前缀、标题或注释。
                            3. 如果问题可以简短回答就直接回答，不要冗长。
                            4. 对于复杂问题，也尽量保持回答直截了当、可理解。
                            5. 回答的句子末尾需要换行。
                        """.trimIndent()
                    )
                }

                user(
                    """
                        当前上下文：
                        $ctx
                    """.trimIndent()
                )
            }

            val agent = AIAgent.Companion(
                promptExecutor = llm,
                agentConfig = AIAgentConfig(
                    prompt = prompt,
                    model = LLMModelsChoice.Pro,
                    maxAgentIterations = 10
                ),
                toolRegistry = ToolRegistry.Companion {
                    tools(WebSearchTool.asTools())
                },
                strategy = getStrategy(group)
            )

            agent.run(
                """
                        用户问题：
                        ${meta.input}
                        请给出直接的答案
                """.trimIndent()
            )
        }
    }

    override fun onUnload() {

    }

    override val matcher: Pair<String, String>
        get() = "DIRECT_QA" to """
        当用户提出的是【明确、严肃、可被直接回答的问题】时，选择此分类。

        判断标准（满足任一即可）：
        - 涉及知识、技术、编程、规则、事实、概念解释
        - 需要准确答案或理性说明
        - 去掉语气和情绪后，问题本身依然成立

        DIRECT_QA 的消息应由 AI 直接回答，
        即使语气随意，只要本质是求解答，也应归类为 DIRECT_QA。
        """.trimIndent()

}