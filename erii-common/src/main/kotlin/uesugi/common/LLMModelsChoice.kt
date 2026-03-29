package uesugi.common

import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel

object LLMModelsChoice {

    private val log = logger()

    private val choice by lazy {
        val choice = ConfigHolder.getChoiceModel()
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
            "MINIMAX" -> miniMaxChat("MiniMax-M2.5")
            else -> throw RuntimeException("Unknown choice model: $choice")
        }
    }

    val Pro by lazy {
        when (choice) {
            "GOOGLE" -> GoogleModels.Gemini2_5Pro
            "DEEP_SEEK" -> DeepSeekChat
            "MINIMAX" -> miniMaxChat("MiniMax-M2.7")
            else -> throw RuntimeException("Unknown choice model: $choice")
        }
    }

}
