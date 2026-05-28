package uesugi.core.component.llm

import ai.koog.http.client.ktor.KtorKoogHttpClient
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAILLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.llm.LLMProvider
import io.ktor.client.*
import uesugi.common.toolkit.ConfigHolder
import uesugi.config.LLMClientProvider
import kotlin.time.ExperimentalTime

class DeepSeekClientProvider : LLMClientProvider {

    override val provider: LLMProvider = LLMProvider.DeepSeek
    override val choiceKey: String = "DEEPSEEK"

    private val apiKey by lazy { ConfigHolder.getLlmDeepSeekApiKey() }
    private val baseUrl by lazy { ConfigHolder.getLlmDeepSeekBaseUrl() }

    @OptIn(ExperimentalTime::class)
    override fun createClient(baseClient: HttpClient): RetryingLLMClient {
        return RetryingLLMClient(
            delegate = OpenAILLMClient(
                apiKey = apiKey,
                settings = OpenAIClientSettings(baseUrl = baseUrl),
                httpClientFactory = KtorKoogHttpClient.Factory(baseClient),
            ),
            config = RetryConfig.CONSERVATIVE
        )
    }
}
