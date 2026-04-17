package uesugi.plugin.animal

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.pf4j.Extension
import uesugi.common.BotManage
import uesugi.common.toolkit.BrowserScraper
import uesugi.common.toolkit.BrowserScraperHolder
import uesugi.common.toolkit.ConfigHolder
import uesugi.plugin.animal.service.AnimalService
import uesugi.plugin.animal.service.DailyTaskService
import uesugi.plugin.animal.store.AnimalStore
import uesugi.spi.CmdExtension
import uesugi.spi.PassiveExtension
import uesugi.spi.PluginContext
import uesugi.spi.getGroup

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

    // 插件服务器配置（默认 fallback，bot 级别优先从 onebot.bots.<key>.server-host 读取）
    private val serverPort = 8888
    private val serverBasePath = "/plugin/animal"

    override fun onLoad(context: PluginContext) {
        this.context = context

        // 初始化存储
        store = AnimalStore(context.kv)

        // 初始化服务
        service = AnimalService(store)

        // 初始化定时任务
        dailyTaskService = DailyTaskService(store, service, context.scheduler)
        dailyTaskService.startDailyTasks()

        // 初始化HTML渲染器
        htmlRenderer = AnimalHtmlRenderer(store, context)
        htmlRenderer.registerHtmlRoutes()

        // 注册命令处理器
        registerCommandHandler()

        // 注册该群的工具集
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
                    serverBasePath,
                )
            }
        }
    }

    private fun registerCommandHandler() {
        context.chain { meta ->
            try {
                val senderId = meta.senderId ?: return@chain
                val groupId = meta.groupId
                val senderIdLong = senderId.toLong()
                val configKey = BotManage.getConfigKey(meta.botId)
                val botConfig = ConfigHolder.getOnebotBots()[configKey]
                val cmdServerHost = botConfig?.serverHost ?: "hostmachine"
                val cmdExternalHost = botConfig?.externalHost ?: cmdServerHost

                val takeScreenshotCallback: (String) -> ByteArray? = { url ->
                    runCatching {
                        BrowserScraperHolder.getInstance().takeFullScreenshot(
                            url = url.replace("http://${cmdExternalHost}", "http://${cmdServerHost}"),
                            width = 1200,
                            quality = 85,
                            type = BrowserScraper.ScreenshotType.JPEG
                        )
                    }.getOrNull()
                }

                val ctx = AnimalContext(
                    senderId = senderIdLong,
                    groupId = groupId,
                    senderNick = senderId,
                    store = store,
                    service = service,
                    sendMessage = { msg ->
                        scope.launch {
                            meta.getGroup().sendMessage(msg)
                        }
                    },
                    createImage = { bytes ->
                        runBlocking {
                            val imageRes = bytes.inputStream().use { it.toExternalResource() }
                            imageRes.use { res ->
                                meta.getGroup().uploadImage(res)
                            }
                        }
                    },
                    serverUrl = "http://${cmdExternalHost}:${serverPort}${server.basePath}",
                    takeScreenshot = takeScreenshotCallback
                )

                meta.parser(ctx)
            } catch (e: Exception) {
                log.error(e) { "Error handling command" }
                try {
                    meta.getGroup().sendMessage("处理命令时出错：${e.message}")
                } catch (e2: Exception) {
                    log.error(e2) { "Failed to send error message" }
                }
            }
        }
    }

    override fun onUnload() {
        dailyTaskService.stopDailyTasks()
        scope.cancel()
        log.info { "AnimalExtension unloaded" }
    }
}
