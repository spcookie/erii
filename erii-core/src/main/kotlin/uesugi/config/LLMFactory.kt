package uesugi.config

import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.logging.*
import uesugi.common.toolkit.ConfigHolder
import uesugi.core.component.llm.DefaultParamPromptExecutor
import uesugi.core.component.llm.FixingPromptExecutor
import uesugi.core.component.llm.TokenUsagePromptExecutor
import uesugi.core.component.usage.TokenUsageRepository
import kotlin.time.ExperimentalTime

class LLMFactory(
    private val providers: List<LLMClientProvider>,
    private val tokenUsageRepository: TokenUsageRepository
) {

    @OptIn(ExperimentalTime::class)
    fun promptExecutor(recordUsage: Boolean = true): PromptExecutor {
        val isDebug = System.getProperty("llm.request.debug")?.toBoolean()
            ?: System.getenv("LLM_REQUEST_DEBUG")?.toBoolean()
            ?: false

        val baseClient = getBaseClient(isDebug)
        val llmClients = providers
            .filter { it.isConfigured() }
            .associate { it.provider to it.createClient(baseClient) }

        val defaultParams = ConfigHolder.getLlmDefaultParams()
        val executor = DefaultParamPromptExecutor(
            FixingPromptExecutor(MultiLLMPromptExecutor(llmClients)),
            defaultParams
        )
        return if (recordUsage) {
            TokenUsagePromptExecutor(executor, tokenUsageRepository)
        } else {
            executor
        }
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
