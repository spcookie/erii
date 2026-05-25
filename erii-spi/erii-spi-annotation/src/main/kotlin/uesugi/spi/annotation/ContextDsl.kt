package uesugi.spi.annotation

import uesugi.spi.*

fun useMem(): Lazy<Mem> = lazy { ContextHolder.get().mem }
fun useKv(): Lazy<Kv> = lazy { ContextHolder.get().kv }
fun useBlob(): Lazy<Blob> = lazy { ContextHolder.get().blob }
fun useVector(): Lazy<Vector> = lazy { ContextHolder.get().vector }
fun useConfig(): Lazy<PluginConfig> = lazy { ContextHolder.get().config }
fun useDatabase(): Lazy<Database> = lazy { ContextHolder.get().database }
fun useScheduler(): Lazy<Scheduler> = lazy { ContextHolder.get().scheduler }
fun useLlm(): Lazy<ai.koog.prompt.executor.model.PromptExecutor> = lazy { ContextHolder.get().llm }
fun useHttp(): Lazy<io.ktor.client.HttpClient> = lazy { ContextHolder.get().http }
fun useServer(): Lazy<Server> = lazy { ContextHolder.get().server }
