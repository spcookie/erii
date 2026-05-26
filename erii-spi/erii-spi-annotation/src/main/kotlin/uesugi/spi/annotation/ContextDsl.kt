package uesugi.spi.annotation

import ai.koog.prompt.executor.model.PromptExecutor
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import uesugi.spi.*

suspend fun useMem(): Mem =
    currentCoroutineContext()[PluginContextElement]?.context?.mem
        ?: error(NO_CONTEXT_ERROR)

suspend fun useKv(): Kv =
    currentCoroutineContext()[PluginContextElement]?.context?.kv
        ?: error(NO_CONTEXT_ERROR)

suspend fun useBlob(): Blob =
    currentCoroutineContext()[PluginContextElement]?.context?.blob
        ?: error(NO_CONTEXT_ERROR)

suspend fun useVector(): Vector =
    currentCoroutineContext()[PluginContextElement]?.context?.vector
        ?: error(NO_CONTEXT_ERROR)

suspend fun useConfig(): PluginConfig =
    currentCoroutineContext()[PluginContextElement]?.context?.config
        ?: error(NO_CONTEXT_ERROR)

suspend fun useDatabase(): Database =
    currentCoroutineContext()[PluginContextElement]?.context?.database
        ?: error(NO_CONTEXT_ERROR)

suspend fun useScheduler(): Scheduler =
    currentCoroutineContext()[PluginContextElement]?.context?.scheduler
        ?: error(NO_CONTEXT_ERROR)

suspend fun useLLM(): PromptExecutor =
    currentCoroutineContext()[PluginContextElement]?.context?.llm
        ?: error(NO_CONTEXT_ERROR)

suspend fun useHttp(): io.ktor.client.HttpClient =
    currentCoroutineContext()[PluginContextElement]?.context?.http
        ?: error(NO_CONTEXT_ERROR)

suspend fun useServer(): Server =
    currentCoroutineContext()[PluginContextElement]?.context?.server
        ?: error(NO_CONTEXT_ERROR)

internal suspend fun withPluginContext(context: PluginContext, block: suspend () -> Unit) {
    withContext(PluginContextElement(context)) {
        block()
    }
}