package uesugi.common

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

    private fun resolveModelId(providerModels: Map<String, String>, tier: String): String {
        val all = providerModels["all"]
        return if (!all.isNullOrBlank()) all else providerModels[tier] ?: ""
    }

    private fun googleModel(tier: String): LLModel {
        val models = ConfigHolder.getLlmGoogleModels()
        val modelId = resolveModelId(models, tier)
        return LLModel(
            provider = LLMProvider.Google,
            id = modelId,
            capabilities = listOf(
                LLMCapability.Completion,
                LLMCapability.PromptCaching,
                LLMCapability.Temperature,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.MultipleChoices
            ),
            contextLength = 128_000,
            maxOutputTokens = 8_000
        )
    }

    private fun deepSeekModel(tier: String): LLModel {
        val models = ConfigHolder.getLlmDeepSeekModels()
        val modelId = resolveModelId(models, tier)
        return LLModel(
            provider = LLMProvider.DeepSeek,
            id = modelId,
            capabilities = listOf(
                LLMCapability.Completion,
                LLMCapability.PromptCaching,
                LLMCapability.Temperature,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.MultipleChoices
            ),
            contextLength = 128_000,
            maxOutputTokens = 8_000
        )
    }

    private fun minimaxModel(tier: String): LLModel {
        val models = ConfigHolder.getLlmMinimaxModels()
        val modelId = resolveModelId(models, tier)
        return LLModel(
            provider = LLMProvider.Anthropic,
            id = modelId,
            capabilities = listOf(
                LLMCapability.Completion,
                LLMCapability.Temperature,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.MultipleChoices
            ),
            contextLength = 204_800,
            maxOutputTokens = 204_800
        )
    }

    val Lite by lazy {
        when (choice) {
            "GOOGLE" -> googleModel("lite")
            "DEEP_SEEK" -> deepSeekModel("lite")
            "MINIMAX" -> minimaxModel("lite")
            else -> throw RuntimeException("Unknown choice model: $choice")
        }
    }

    val Flash by lazy {
        when (choice) {
            "GOOGLE" -> googleModel("flash")
            "DEEP_SEEK" -> deepSeekModel("flash")
            "MINIMAX" -> minimaxModel("flash")
            else -> throw RuntimeException("Unknown choice model: $choice")
        }
    }

    val Pro by lazy {
        when (choice) {
            "GOOGLE" -> googleModel("pro")
            "DEEP_SEEK" -> deepSeekModel("pro")
            "MINIMAX" -> minimaxModel("pro")
            else -> throw RuntimeException("Unknown choice model: $choice")
        }
    }

}
