package uesugi.plugin.animal

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.pf4j.Extension
import uesugi.common.toolkit.BrowserScraper
import uesugi.common.toolkit.BrowserScraperHolder
import uesugi.plugin.animal.cmd.AnimalArgParser
import uesugi.plugin.animal.cmd.AnimalContext
import uesugi.plugin.animal.core.Mode
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
                config.getString("browser.status-host")
            }.getOrNull() ?: serverHost
        }

        // 初始化存储
        store = AnimalStore(context.kv)

        // 初始化服务
        service = AnimalService(store)

        // 初始化定时任务
        dailyTaskService = DailyTaskService(store, service, context.scheduler)
        dailyTaskService.startDailyTasks()

        // 注册HTML路由
        registerHtmlRoutes()

        // 注册命令处理器
        registerCommandHandler()

        // 注册该群的工具集
        registerTools()

        log.info { "AnimalExtension loaded" }
    }

    private fun registerHtmlRoutes() {
        context.server.route {
            // 宠物详情HTML路由
            get("/pet/{groupId}/{userId}/{petId}") {
                val groupId = call.parameters["groupId"] ?: return@get
                val userId = call.parameters["userId"]?.toLongOrNull() ?: return@get
                val petId = call.parameters["petId"]?.toLongOrNull() ?: return@get

                val html = runCatching {
                    getPetHtml(groupId, userId, petId)
                }.getOrNull()

                if (html != null) {
                    call.respondText(html, contentType = ContentType.Text.Html)
                } else {
                    call.respondText("Pet not found", status = HttpStatusCode.NotFound)
                }
            }

            // 农场HTML路由
            get("/farm/{groupId}/{userId}") {
                val groupId = call.parameters["groupId"] ?: return@get
                val userId = call.parameters["userId"]?.toLongOrNull() ?: return@get

                val html = runCatching {
                    getFarmHtml(groupId, userId)
                }.getOrNull()

                if (html != null) {
                    call.respondText(html, contentType = ContentType.Text.Html)
                } else {
                    call.respondText("Farm not found", status = HttpStatusCode.NotFound)
                }
            }
        }
    }

    private fun renderHtml(svgContent: String): String {
        return """
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Erii Animal</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body {
            display: flex;
            justify-content: center;
            align-items: center;
            min-height: 100vh;
            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
        }
        .container {
            background: white;
            border-radius: 20px;
            box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
            overflow: hidden;
        }
        svg { display: block; width: 100%; height: auto; }
    </style>
</head>
<body>
    <div class="container">
        $svgContent
    </div>
</body>
</html>
        """.trimIndent()
    }

    suspend fun getPetHtml(groupId: String, userId: Long, petId: Long): String? {
        val user = store.getUser(groupId, userId) ?: return null
        val svg = user.createLineAnimation(petId, Mode.LINE)
        return renderHtml(svg)
    }

    suspend fun getFarmHtml(groupId: String, userId: Long): String? {
        val user = store.getUser(groupId, userId) ?: return null
        val svg = user.createFarmAnimation()
        return renderHtml(svg)
    }

    private fun registerTools() {
        val serverUrl = "http://${serverHost}:${serverPort}/plugin/animal"

        context.tool {
            {
                AnimalToolSet(
                    store,
                    service,
                    serverUrl
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
                val serverUrl = "http://${serverHost}:${serverPort}/plugin/animal"

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
