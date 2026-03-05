package uesugi.config

import ai.koog.prompt.executor.clients.anthropic.AnthropicClientSettings
import ai.koog.prompt.executor.clients.anthropic.AnthropicLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekClientSettings
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.google.GoogleClientSettings
import ai.koog.prompt.executor.clients.google.GoogleLLMClient
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.clients.retry.RetryConfig
import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import io.ktor.client.*
import io.ktor.client.engine.*
import uesugi.toolkit.logger

class LLMFactory {

    fun promptExecutor(): PromptExecutor {
        val googleApiKey = System.getenv("GOOGLE_API_KEY")
        val googleBaseUrl = System.getenv("GOOGLE_BASE_URL")
        val deepSeekApiKey = System.getenv("DEEP_SEEK_API_KEY")
        val deepSeekBaseUrl = System.getenv("DEEP_SEEK_BASE_URL")
        val minimaxApiKey = System.getenv("MINIMAX_API_KEY")
        val minimaxBaseUrl = System.getenv("MINIMAX_BASE_URL")

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
                            },
                            settings = GoogleClientSettings(
                                baseUrl = googleBaseUrl.takeIf { !it.isNullOrBlank() }
                                    ?: "https://generativelanguage.googleapis.com"
                            )
                        ),
                        config = RetryConfig.CONSERVATIVE
                    ))
            }
            if (!deepSeekApiKey.isNullOrBlank()) {
                put(
                    LLMProvider.DeepSeek,
                    RetryingLLMClient(
                        delegate = DeepSeekLLMClient(
                            apiKey = deepSeekApiKey,
                            settings = DeepSeekClientSettings(
                                baseUrl = deepSeekBaseUrl.takeIf { !it.isNullOrBlank() } ?: "https://api.deepseek.com"
                            )
                        ),
                        config = RetryConfig.CONSERVATIVE
                    )
                )
            }
            if (!minimaxApiKey.isNullOrBlank())
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
                                    LLMModelsChoice.Pro to "MiniMax-M2.5",
                                    LLMModelsChoice.Flash to "MiniMax-M2.1"
                                ),
                                baseUrl = minimaxBaseUrl.takeIf { !it.isNullOrBlank() }
                                    ?: "https://api.minimaxi.com",
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

object LLMModelsChoice {

    private val log = logger()

    private val choice by lazy {
        val choice = System.getenv("CHOICE_MODEL").takeIf { !it.isNullOrBlank() } ?: "GOOGLE"
        log.info("apply llm choice: $choice")
        choice
    }

    private val DeepSeekChat: LLModel = LLModel(
        provider = LLMProvider.DeepSeek,
        id = "deepseek-chat",
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.PromptCaching,
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.MultipleChoices,
        ),
        contextLength = 128_000,
        maxOutputTokens = 8_000
    )

    private fun miniMaxChat(model: String): LLModel = LLModel(
        provider = LLMProvider.Anthropic,
        id = model,
        capabilities = listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.Tools,
            LLMCapability.ToolChoice,
            LLMCapability.MultipleChoices
        ),
        contextLength = 204800,
        maxOutputTokens = 204800
    )

    val Lite by lazy {
        when (choice) {
            "GOOGLE" -> GoogleModels.Gemini2_5FlashLite
            "DEEP_SEEK", "MINIMAX" -> DeepSeekChat
            else -> throw RuntimeException("Unknown choice model: $choice")
        }
    }

    val Flash by lazy {
        when (choice) {
            "GOOGLE" -> GoogleModels.Gemini2_5Flash
            "DEEP_SEEK" -> DeepSeekChat
            "MINIMAX" -> miniMaxChat("MiniMax-M2.1")
            else -> throw RuntimeException("Unknown choice model: $choice")
        }
    }

    val Pro by lazy {
        when (choice) {
            "GOOGLE" -> GoogleModels.Gemini2_5Pro
            "DEEP_SEEK" -> DeepSeekChat
            "MINIMAX" -> miniMaxChat("MiniMax-M2.5")
            else -> throw RuntimeException("Unknown choice model: $choice")
        }
    }

}