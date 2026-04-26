package uesugi.plugin.animal

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.pf4j.Extension
import uesugi.plugin.animal.service.AnimalService
import uesugi.plugin.animal.service.DailyTaskService
import uesugi.plugin.animal.store.AnimalStore
import uesugi.spi.CmdExtension
import uesugi.spi.PassiveExtension
import uesugi.spi.PluginContext

@Extension
class AnimalExtension : PassiveExtension<Animal>, CmdExtension<AnimalContext, AnimalArgParser, Animal> {

    override val cmd: String = "animal"

    private val log = KotlinLogging.logger {}

    private lateinit var context: PluginContext
    private lateinit var store: AnimalStore
    private lateinit var service: AnimalService
    private lateinit var dailyTaskService: DailyTaskService
    private lateinit var htmlRenderer: AnimalHtmlRenderer
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val serverPort = 8888

    override fun onLoad(context: PluginContext) {
        this.context = context

        store = AnimalStore(context.kv)
        service = AnimalService(store)

        dailyTaskService = DailyTaskService(store, service, context.scheduler)
        dailyTaskService.startDailyTasks()

        htmlRenderer = AnimalHtmlRenderer(store, context)
        htmlRenderer.registerHtmlRoutes()

        registerCommandHandler()
        registerTools()

        log.info { "AnimalExtension loaded" }
    }

    private fun registerTools() {
        context.tool {
            {
                AnimalToolSet(
                    store,
                    service,
                    serverPort,
                    context.server.basePath,
                )
            }
        }
    }

    private fun registerCommandHandler() {
        context.chain { meta ->
            AnimalCommandHandler(
                store = store,
                service = service,
                serverPort = serverPort,
                serverBasePath = context.server.basePath,
                scope = scope
            ).handleWithError(meta) { ctx ->
                meta.parser(ctx)
            }
        }
    }

    override fun onUnload() {
        dailyTaskService.stopDailyTasks()
        scope.cancel()
        log.info { "AnimalExtension unloaded" }
    }
}
