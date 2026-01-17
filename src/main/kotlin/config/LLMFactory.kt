package uesugi.config

import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import io.ktor.client.*
import io.ktor.client.engine.*

class LLMFactory {

    fun promptExecutor(): PromptExecutor {
        val llmClient = GoogleLLMClient(
            apiKey = System.getenv("GOOGLE_API_KEY"),
            baseClient = HttpClient {
                engine {
                    val httpProxy = System.getenv("HTTP_PROXY")
                    if (httpProxy != null) {
                        proxy = ProxyBuilder.http(httpProxy)
                    }
                }
            }
        )

        val resilientClient = RetryingLLMClient(
            delegate = llmClient,
            config = RetryConfig.CONSERVATIVE
        )

        return SingleLLMPromptExecutor(resilientClient)
    }

}