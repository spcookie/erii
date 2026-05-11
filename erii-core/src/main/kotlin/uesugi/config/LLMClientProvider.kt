package uesugi.config

import ai.koog.prompt.executor.clients.retry.RetryingLLMClient
import ai.koog.prompt.llm.LLMProvider
import io.ktor.client.*
import uesugi.common.toolkit.ConfigHolder

interface LLMClientProvider {
    val provider: LLMProvider
    val choiceKey: String
    fun isConfigured(): Boolean = ConfigHolder.getChoiceProvider() == choiceKey
    fun createClient(baseClient: HttpClient): RetryingLLMClient
}