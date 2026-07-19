package uesugi.core.component.llm

import ai.koog.prompt.executor.clients.openai.base.models.OpenAIFunction
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIMessage
import ai.koog.prompt.executor.clients.openai.base.models.OpenAIToolCall
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageAwareOpenAILLMClientTest {

    @Test
    fun `normalizes double encoded historical tool arguments`() {
        val arguments = """{"texts":["hello"]}"""
        val messages = listOf(
            assistantToolCall(Json.encodeToString(arguments))
        )

        val normalized = normalizeToolCallArguments(messages)
        val toolCall = (normalized.single() as OpenAIMessage.Assistant).toolCalls!!.single()

        assertEquals(arguments, toolCall.function.arguments)
    }

    @Test
    fun `keeps correctly encoded tool arguments unchanged`() {
        val arguments = """{"texts":["hello"]}"""
        val messages = listOf(assistantToolCall(arguments))

        val normalized = normalizeToolCallArguments(messages)
        val toolCall = (normalized.single() as OpenAIMessage.Assistant).toolCalls!!.single()

        assertEquals(arguments, toolCall.function.arguments)
    }

    private fun assistantToolCall(arguments: String): OpenAIMessage.Assistant =
        OpenAIMessage.Assistant(
            toolCalls = listOf(
                OpenAIToolCall(
                    id = "tool-call-id",
                    function = OpenAIFunction(
                        name = "sendText",
                        arguments = arguments,
                    ),
                )
            )
        )
}
