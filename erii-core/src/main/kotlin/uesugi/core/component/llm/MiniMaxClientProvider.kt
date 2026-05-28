package uesugi.core.component.llm

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.llm.LLMProvider
import io.ktor.client.*
import uesugi.common.LLMProviderChoice
import uesugi.common.toolkit.ConfigHolder
import uesugi.config.LLMClientProvider
import kotlin.time.ExperimentalTime

class MiniMaxClientProvider : LLMClientProvider {

    override val provider: LLMProvider = LLMProvider.Anthropic
    override val choiceKey: String = "MINIMAX"

    private val apiKey by lazy { ConfigHolder.getLlmMinimaxApiKey() }
    private val baseUrl by lazy { ConfigHolder.getLlmMinimaxBaseUrl() }

    @OptIn(ExperimentalTime::class)
    override fun createClient(baseClient: HttpClient): RetryingLLMClient {
        return RetryingLLMClient(
            delegate = AnthropicLLMClient(
                apiKey = apiKey,
                settings = AnthropicClientSettings(
                    modelVersionsMap = mapOf(
                        LLMProviderChoice.Pro to LLMProviderChoice.Pro.id,
                        LLMProviderChoice.Flash to LLMProviderChoice.Flash.id
                    ),
                    baseUrl = baseUrl,
                    messagesPath = "anthropic/v1/messages"
                ),
                httpClientFactory = KtorKoogHttpClient.Factory(baseClient),
            ),
            config = RetryConfig.CONSERVATIVE
        )
    }
}
