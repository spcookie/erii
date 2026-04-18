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
import io.ktor.client.plugins.logging.*
import uesugi.common.LLMModelsChoice
import uesugi.common.toolkit.ConfigHolder
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

        val isDebug = System.getProperty("llm.request.debug")?.toBoolean()
            ?: System.getenv("LLM_REQUEST_DEBUG")?.toBoolean()
            ?: false

        val llmClients = buildMap {
            if (googleApiKey.isNotBlank()) {
                put(
                    LLMProvider.Google,
                    RetryingLLMClient(
                        delegate = GoogleLLMClient(
                            apiKey = googleApiKey,
                            baseClient = getBaseClient(isDebug),
                            settings = GoogleClientSettings(
                                baseUrl = googleBaseUrl
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
                            ),
                            baseClient = getBaseClient(isDebug),
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
                            baseClient = getBaseClient(isDebug),
                            apiKey = minimaxApiKey,
                            settings = AnthropicClientSettings(
                                modelVersionsMap = mapOf(
                                    LLMModelsChoice.Pro to LLMModelsChoice.Pro.id,
                                    LLMModelsChoice.Flash to LLMModelsChoice.Flash.id
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

    fun getBaseClient(isDebug: Boolean): HttpClient = HttpClient {
        engine {
            val httpProxy = ConfigHolder.getProxyHttp()
            if (httpProxy != null) {
                proxy = ProxyBuilder.http(httpProxy)
            }
            if (isDebug) {
                install(Logging) {
                    logger = Logger.DEFAULT
                    level = LogLevel.ALL
                }
            }
        }
    }

}