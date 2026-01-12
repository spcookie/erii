package uesugi.config

import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.SingleLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import io.ktor.client.*
import io.ktor.client.engine.*

class LLMFactory(val httpProxy: String?) {

    fun promptExecutor(): PromptExecutor {
        //val llmClient = GoogleLLMClient(System.getenv("GOOGLE_API_KEY"))
        val llmClient = GoogleLLMClient(
            apiKey = System.getenv("GOOGLE_API_KEY"),
            baseClient = HttpClient {
                engine {
                    if (httpProxy != null) {
                        proxy = ProxyBuilder.http(httpProxy)
                    }
                }
//                install(Logging) {
//                    logger = Logger.DEFAULT
//                    level = LogLevel.ALL
//                    sanitizeHeader { header -> header == HttpHeaders.Authorization }
//                }
            }
        )

        val resilientClient = RetryingLLMClient(
            delegate = llmClient,
            config = RetryConfig.CONSERVATIVE
        )

        return SingleLLMPromptExecutor(resilientClient)
    }

}