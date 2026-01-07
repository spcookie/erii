package uesugi

import ai.koog.agents.core.agent.asMermaidDiagram
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.*
import uesugi.core.botPrompt
import uesugi.core.volition.InterruptionMode
import uesugi.core.volition.ProactiveSpeakEvent
import kotlin.test.Test

class ApplicationTest {

    @Test
    fun testRoot() {
        val diagram = strategy("聊天") {
            val setupContext by nodeAppendPrompt<String>("setupContext") {
                messages(
                    botPrompt(
                        BotProxy.currentBot.id.toString(),
                        "1053148332",
                        ProactiveSpeakEvent(10.0, InterruptionMode.Interrupt)
                    ).messages
                )
            }

            val logNode by node<String, String> {
                it
            }

            val nodeSendInput by nodeLLMRequest()
            val nodeExecuteTool by nodeExecuteTool()
            val nodeSendToolResult by nodeLLMSendToolResult()

            edge(nodeStart forwardTo setupContext)
            edge(setupContext forwardTo logNode)
            edge(logNode forwardTo nodeSendInput)
            edge(nodeSendInput forwardTo nodeExecuteTool onToolCall { true })
            edge(nodeExecuteTool forwardTo nodeSendToolResult)
            edge(nodeSendToolResult forwardTo nodeFinish)
            edge(nodeSendInput forwardTo nodeFinish onAssistantMessage { true })
        }.asMermaidDiagram()
        println(diagram)
    }

}
