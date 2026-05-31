package uesugi.common

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import uesugi.common.toolkit.ConfigHolder
import uesugi.common.toolkit.logger

object LLMProviderChoice {

    private val log = logger()

    private val choice by lazy {
        val choice = ConfigHolder.getChoiceProvider()
        log.info("apply llm provider: $choice")
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
                LLMCapability.MultipleChoices,
                LLMCapability.Thinking
            ),
            contextLength = 204_800,
            maxOutputTokens = 204_800
        )
    }

    private fun openaiModel(tier: String): LLModel {
        val models = ConfigHolder.getLlmOpenAIModels()
        val modelId = resolveModelId(models, tier)
        return LLModel(
            provider = LLMProvider.OpenAI,
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
            maxOutputTokens = 16_000
        )
    }

    private fun anthropicModel(tier: String): LLModel {
        val models = ConfigHolder.getLlmAnthropicModels()
        val modelId = resolveModelId(models, tier)
        return LLModel(
            provider = LLMProvider.Anthropic,
            id = modelId,
            capabilities = listOf(
                LLMCapability.Completion,
                LLMCapability.PromptCaching,
                LLMCapability.Temperature,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.MultipleChoices
            ),
            contextLength = 200_000,
            maxOutputTokens = 16_000
        )
    }

    private fun openrouterModel(tier: String): LLModel {
        val models = ConfigHolder.getLlmOpenRouterModels()
        val modelId = resolveModelId(models, tier)
        return LLModel(
            provider = LLMProvider.OpenRouter,
            id = modelId,
            capabilities = listOf(
                LLMCapability.Completion,
                LLMCapability.Temperature,
                LLMCapability.Tools,
                LLMCapability.ToolChoice,
                LLMCapability.MultipleChoices
            ),
            contextLength = 128_000,
            maxOutputTokens = 16_000
        )
    }

    val Lite by lazy {
        when (choice) {
            "GOOGLE" -> googleModel("lite")
            "DEEP_SEEK" -> deepSeekModel("lite")
            "MINIMAX" -> minimaxModel("lite")
            "OPENAI" -> openaiModel("lite")
            "ANTHROPIC" -> anthropicModel("lite")
            "OPENROUTER" -> openrouterModel("lite")
            else -> throw RuntimeException("Unknown choice provider: $choice")
        }
    }

    val Flash by lazy {
        when (choice) {
            "GOOGLE" -> googleModel("flash")
            "DEEP_SEEK" -> deepSeekModel("flash")
            "MINIMAX" -> minimaxModel("flash")
            "OPENAI" -> openaiModel("flash")
            "ANTHROPIC" -> anthropicModel("flash")
            "OPENROUTER" -> openrouterModel("flash")
            else -> throw RuntimeException("Unknown choice provider: $choice")
        }
    }

    val Pro by lazy {
        when (choice) {
            "GOOGLE" -> googleModel("pro")
            "DEEP_SEEK" -> deepSeekModel("pro")
            "MINIMAX" -> minimaxModel("pro")
            "OPENAI" -> openaiModel("pro")
            "ANTHROPIC" -> anthropicModel("pro")
            "OPENROUTER" -> openrouterModel("pro")
            else -> throw RuntimeException("Unknown choice provider: $choice")
        }
    }

}
