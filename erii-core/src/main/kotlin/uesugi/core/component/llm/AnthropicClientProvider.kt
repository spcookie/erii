package uesugi.core.component.llm

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.llm.LLMProvider
import io.ktor.client.*
import uesugi.common.toolkit.ConfigHolder
import uesugi.config.LLMClientProvider
import kotlin.time.ExperimentalTime

class AnthropicClientProvider : LLMClientProvider {

    override val provider: LLMProvider = LLMProvider.Anthropic
    override val choiceKey: String = "ANTHROPIC"

    private val apiKey by lazy { ConfigHolder.getLlmAnthropicApiKey() }
    private val baseUrl by lazy { ConfigHolder.getLlmAnthropicBaseUrl() }

    @OptIn(ExperimentalTime::class)
    override fun createClient(baseClient: HttpClient): RetryingLLMClient {
        return RetryingLLMClient(
            delegate = AnthropicLLMClient(
                apiKey = apiKey,
                settings = AnthropicClientSettings(baseUrl = baseUrl),
                httpClientFactory = KtorKoogHttpClient.Factory(baseClient),
            ),
            config = RetryConfig.CONSERVATIVE
        )
    }
}
