package uesugi.core.component.llm

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.openai.models.OpenAIChatCompletionResponse
import ai.koog.prompt.message.Message
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class UsageAwareOpenAILLMClient(
    apiKey: String,
    settings: OpenAIClientSettings,
    httpClientFactory: KoogHttpClient.Factory,
) : OpenAILLMClient(apiKey, settings, httpClientFactory) {

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
