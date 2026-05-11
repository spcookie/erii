package uesugi.core.component.llm

import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.llm.LLMProvider
import io.ktor.client.*
import uesugi.common.toolkit.ConfigHolder
import uesugi.config.LLMClientProvider
import kotlin.time.ExperimentalTime

class GoogleClientProvider : LLMClientProvider {

    override val provider: LLMProvider = LLMProvider.Google
    override val choiceKey: String = "GOOGLE"

    private val apiKey by lazy { ConfigHolder.getLlmGoogleApiKey() }
    private val baseUrl by lazy { ConfigHolder.getLlmGoogleBaseUrl() }

    @OptIn(ExperimentalTime::class)
    override fun createClient(baseClient: HttpClient): RetryingLLMClient {
        return RetryingLLMClient(
            delegate = GoogleLLMClient(
                apiKey = apiKey,
                baseClient = baseClient,
                settings = GoogleClientSettings(baseUrl = baseUrl)
            ),
            config = RetryConfig.CONSERVATIVE
        )
    }
}
