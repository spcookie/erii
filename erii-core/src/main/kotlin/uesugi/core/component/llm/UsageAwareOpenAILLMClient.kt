package uesugi.core.component.llm

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.base.models.*
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionResponse
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.params.LLMParams
import kotlinx.serialization.json.*

class UsageAwareOpenAILLMClient(
    apiKey: String,
    settings: OpenAIClientSettings,
    httpClientFactory: KoogHttpClient.Factory,
) : OpenAILLMClient(apiKey, settings, httpClientFactory) {

    override fun serializeProviderChatRequest(
        messages: List<OpenAIMessage>,
        model: LLModel,
        tools: List<OpenAITool>?,
        toolChoice: OpenAIToolChoice?,
        params: LLMParams,
        stream: Boolean,
    ): String = super.serializeProviderChatRequest(
        messages = normalizeToolCallArguments(messages),
        model = model,
        tools = tools,
        toolChoice = toolChoice,
        params = params,
        stream = stream,
    )

    override fun processProviderChatResponse(response: OpenAIChatCompletionResponse): List<Message.Assistant> {
        val results = super.processProviderChatResponse(response)

        val usage = response.usage ?: return results
        val cachedTokens = usage.promptTokensDetails?.cachedTokens
        val reasoningTokens = usage.completionTokensDetails?.reasoningTokens

        if (cachedTokens == null && reasoningTokens == null) return results

        val extraMetadata = buildJsonObject {
            if (cachedTokens != null) put("cached_tokens", cachedTokens)
            if (reasoningTokens != null) put("reasoning_tokens", reasoningTokens)
        }

        return results.map { assistant ->
            val existing = assistant.metaInfo.metadata
            val merged = if (existing != null) {
                JsonObject(existing.toMap() + extraMetadata.toMap())
            } else {
                extraMetadata
            }
            assistant.copy(metaInfo = assistant.metaInfo.copy(metadata = merged))
        }
    }
}

internal fun normalizeToolCallArguments(messages: List<OpenAIMessage>): List<OpenAIMessage> =
    messages.map { message ->
        if (message !is OpenAIMessage.Assistant) {
            return@map message
        }
        val toolCalls = message.toolCalls ?: return@map message

        OpenAIMessage.Assistant(
            content = message.content,
            reasoningContent = message.reasoningContent,
            audio = message.audio,
            name = message.name,
            refusal = message.refusal,
            toolCalls = toolCalls.map { call ->
                OpenAIToolCall(
                    id = call.id,
                    function = OpenAIFunction(
                        name = call.function.name,
                        arguments = decodeDoubleEncodedToolArguments(call.function.arguments),
                    ),
                )
            },
            annotations = message.annotations,
        )
    }

private fun decodeDoubleEncodedToolArguments(arguments: String): String {
    val decoded = runCatching {
        Json.decodeFromString<String>(arguments)
    }.getOrNull() ?: return arguments

    val isJsonObject = runCatching {
        Json.parseToJsonElement(decoded).jsonObject
    }.isSuccess
    return if (isJsonObject) decoded else arguments
}
