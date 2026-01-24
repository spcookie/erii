package uesugi.config

import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import io.ktor.client.*
import io.ktor.client.engine.*

class LLMFactory {

    fun promptExecutor(): PromptExecutor {
        val googleApiKey = System.getenv("GOOGLE_API_KEY")
        val deepSeekApiKey = System.getenv("DEEP_SEEK_API_KEY")

        val llmClients = buildMap {
            if (!googleApiKey.isNullOrBlank()) {
                put(
                    LLMProvider.Google,
                    RetryingLLMClient(
                        delegate = GoogleLLMClient(
                            apiKey = googleApiKey,
                            baseClient = HttpClient {
                                engine {
                                    val httpProxy = System.getenv("HTTP_PROXY")
                                    if (httpProxy != null) {
                                        proxy = ProxyBuilder.http(httpProxy)
                                    }
//                                    install(Logging) {
//                                        logger = Logger.DEFAULT
//                                        level = LogLevel.ALL
//                                    }
                                }
                            }
                        ),
                        config = RetryConfig.CONSERVATIVE
                    ))
            }
            if (!deepSeekApiKey.isNullOrBlank()) {
                put(
                    LLMProvider.DeepSeek,
                    RetryingLLMClient(
                        delegate = DeepSeekLLMClient(deepSeekApiKey),
                        config = RetryConfig.CONSERVATIVE
                    )
                )
            }
        }


        return MultiLLMPromptExecutor(llmClients)
    }

}