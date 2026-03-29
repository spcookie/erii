package uesugi.config

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMProvider
import io.ktor.client.*
import io.ktor.client.engine.*
import uesugi.common.ConfigHolder
import uesugi.common.LLMModelsChoice
import kotlin.time.ExperimentalTime

class LLMFactory {

    @OptIn(ExperimentalTime::class)
    fun promptExecutor(): PromptExecutor {
        val googleApiKey = ConfigHolder.getLlmGoogleApiKey()
        val googleBaseUrl = ConfigHolder.getLlmGoogleBaseUrl()
        val deepSeekApiKey = ConfigHolder.getLlmDeepSeekApiKey()
        val deepSeekBaseUrl = ConfigHolder.getLlmDeepSeekBaseUrl()
        val minimaxApiKey = ConfigHolder.getLlmMinimaxApiKey()
        val minimaxBaseUrl = ConfigHolder.getLlmMinimaxBaseUrl()

        val llmClients = buildMap {
            if (googleApiKey.isNotBlank()) {
                put(
                    LLMProvider.Google,
                    RetryingLLMClient(
                        delegate = GoogleLLMClient(
                            apiKey = googleApiKey,
                            baseClient = HttpClient {
                                engine {
                                    val httpProxy = ConfigHolder.getProxyHttp()
                                    if (httpProxy != null) {
                                        proxy = ProxyBuilder.http(httpProxy)
                                    }
//                                    install(Logging) {
//                                        logger = Logger.DEFAULT
//                                        level = LogLevel.ALL
//                                    }
                                }
                            },
                            settings = GoogleClientSettings(
                                baseUrl = googleBaseUrl.takeIf { it.isNotBlank() }
                                    ?: "https://generativelanguage.googleapis.com"
                            )
                        ),
                        config = RetryConfig.CONSERVATIVE
                    ))
            }
            if (deepSeekApiKey.isNotBlank()) {
                put(
                    LLMProvider.DeepSeek,
                    RetryingLLMClient(
                        delegate = DeepSeekLLMClient(
                            apiKey = deepSeekApiKey,
                            settings = DeepSeekClientSettings(
                                baseUrl = deepSeekBaseUrl
                            )
                        ),
                        config = RetryConfig.CONSERVATIVE
                    )
                )
            }
            if (minimaxApiKey.isNotBlank())
                put(
                    LLMProvider.Anthropic,
                    RetryingLLMClient(
                        delegate = AnthropicLLMClient(
                            baseClient = HttpClient {
                                engine {
//                                    install(Logging) {
//                                        logger = Logger.DEFAULT
//                                        level = LogLevel.ALL
//                                    }
                                }
                            },
                            apiKey = minimaxApiKey,
                            settings = AnthropicClientSettings(
                                modelVersionsMap = mapOf(
                                    LLMModelsChoice.Pro to "MiniMax-M2.7",
                                    LLMModelsChoice.Flash to "MiniMax-M2.5"
                                ),
                                baseUrl = minimaxBaseUrl,
                                messagesPath = "anthropic/v1/messages"
                            )
                        ),
                        config = RetryConfig.CONSERVATIVE
                    )
                )
        }


        return MultiLLMPromptExecutor(llmClients)
    }

}