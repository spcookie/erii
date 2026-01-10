package uesugi.config

import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import io.ktor.client.*
import io.ktor.client.engine.*

object LLMFactory {

    fun promptExecutor(): PromptExecutor {
        //val llmClient = GoogleLLMClient(System.getenv("GOOGLE_API_KEY"))
        val llmClient = GoogleLLMClient(
            apiKey = "AIzaSyB3nTHIEX6ah0ITALCMnfYYkIJsoGQI7OE",
            baseClient = HttpClient {
                engine {
                    proxy = ProxyBuilder.http("http://127.0.0.1:9674")
                }
            },
            settings = GoogleClientSettings(

            )
        )

        val resilientClient = RetryingLLMClient(
            delegate = llmClient,
            config = RetryConfig.CONSERVATIVE
        )

        return SingleLLMPromptExecutor(resilientClient)
    }

}