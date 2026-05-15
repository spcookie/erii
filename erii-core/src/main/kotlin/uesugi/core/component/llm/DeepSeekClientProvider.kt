package uesugi.core.component.llm

import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
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
            delegate = DeepSeekLLMClient(
                apiKey = apiKey,
                settings = DeepSeekClientSettings(baseUrl = baseUrl),
                baseClient = baseClient,
            ),
            config = RetryConfig.CONSERVATIVE
        )
    }
}
