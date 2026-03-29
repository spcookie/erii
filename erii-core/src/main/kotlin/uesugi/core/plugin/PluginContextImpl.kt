package uesugi.core.plugin

import ai.koog.prompt.executor.model.PromptExecutor
import io.ktor.client.*
import kotlinx.coroutines.*
import uesugi.BotManage
import uesugi.common.EventBus
import uesugi.common.logger
import uesugi.core.route.MetaToolSetRegister
import uesugi.core.route.RouteCallEvent
import uesugi.spi.*

class PluginContextImpl(
    override val defined: PluginDef,
    override val mem: Mem,
    override val kv: Kv,
    override val blob: Blob,
    override val vector: Vector,
    override val config: PluginConfig,
    override val database: Database,
    override val scheduler: Scheduler,
    override val llm: PromptExecutor,
    override val http: HttpClient,
    override val server: Server,
    val httpProxy: HttpClient
) : PluginContextBootstrap {

    companion object {
        val log = logger()
    }

    private val scope =
        CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("ImageCreator") + CoroutineExceptionHandler { _, exception ->
            log.error("Plugin context detected error: {}", exception.message, exception)
        })

    private lateinit var job: Job

    private val handlers = mutableListOf<Handler>()
    private val toolsets = mutableListOf<MetaToolSetCreator>()

    override val HttpClient.proxy: HttpClient
        get() = httpProxy

    override fun chain(handler: Handler) {
        handlers += handler
    }

    override fun tool(toolset: PluginContext.() -> MetaToolSetCreator) {
        toolsets += toolset(this)
    }

    override fun open() {
        job = EventBus.subscribeAsync<RouteCallEvent>(scope) { event ->
            for (rk in defined.routeKeys) {
                if (event hit rk.key) {
                    val meta = MetaImpl(
                        botId = event.botId,
                        groupId = event.groupId,
                        roledBot = BotManage.getBot(event.botId),
                        input = event.input,
                        senderId = event.senderId,
                        echo = event.echo
                    )
                    for (handler in handlers) {
                        handler(meta)
                    }
                }
            }
        }
    }

    override fun ready() {
        for (toolset in toolsets) {
            MetaToolSetRegister.addToolSet(defined.name, toolset)
        }
    }

    override fun close() {
        if (this::job.isInitialized) {
            EventBus.unsubscribeAsync(job)
        }
        scheduler.close()
    }

}
