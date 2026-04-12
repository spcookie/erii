package uesugi.plugin.animal

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.pf4j.Extension
import uesugi.common.toolkit.BrowserScraper
import uesugi.common.toolkit.BrowserScraperHolder
import uesugi.plugin.animal.cmd.AnimalArgParser
import uesugi.plugin.animal.cmd.AnimalContext
import uesugi.plugin.animal.service.AnimalService
import uesugi.plugin.animal.service.DailyTaskService
import uesugi.plugin.animal.store.AnimalStore
import uesugi.plugin.animal.tool.AnimalToolSet
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

    // 插件服务器配置
    private val serverPort = 8888
    private var serverHost = "hostmachine"

    override fun onLoad(context: PluginContext) {
        this.context = context

        // 尝试从配置读取 server host（如果插件配置中有的话）
        runCatching {
            val config = context.config()
            serverHost = runCatching {
                config.getString("browser.host")
            }.getOrNull() ?: serverHost
        }

        // 初始化存储
        store = AnimalStore(context.kv)

        // 初始化服务
        service = AnimalService(store)

        // 初始化定时任务
        dailyTaskService = DailyTaskService(store, service, context.scheduler)
        dailyTaskService.startDailyTasks()

        // 初始化HTML渲染器
        htmlRenderer = AnimalHtmlRenderer(store, service, context)
        htmlRenderer.registerHtmlRoutes()

        // 注册命令处理器
        registerCommandHandler()

        // 注册该群的工具集
        registerTools()

        log.info { "AnimalExtension loaded" }
    }

    private fun registerTools() {
        val serverUrl = "http://${serverHost}:${serverPort}/plugin/animal"

        context.tool {
            {
                AnimalToolSet(
                    store,
                    service,
                    serverUrl,
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
                val serverUrl = "http://${serverHost}:${serverPort}${server.basePath}"

                val sendImageCallback: (ByteArray) -> Unit = { bytes ->
                    scope.launch {
                        val image = bytes.inputStream().use { it.toExternalResource() }
                        image.use {
                            meta.getGroup().sendImage(it)
                        }
                    }
                }

                val takeScreenshotCallback: (String) -> ByteArray? = { url ->
                    runCatching {
                        BrowserScraperHolder.getInstance().takeFullScreenshot(
                            url = url,
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
                    sendMessage = {
                        scope.launch {
                            meta.getGroup().sendMessage(it)
                        }
                    },
                    sendImage = sendImageCallback,
                    serverUrl = serverUrl,
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
