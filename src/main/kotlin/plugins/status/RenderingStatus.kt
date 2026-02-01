package uesugi.plugins.status

import kotlinx.coroutines.*
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import plugins.Plugin
import uesugi.BotManage
import uesugi.core.CmdRouteRule
import uesugi.core.RouteCallEvent
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import uesugi.toolkit.ref

class RenderingStatus : Plugin {

    private val log = logger()

    val scope =
        CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("RenderingStatus") + CoroutineExceptionHandler { _, exception ->
            log.error("Lolisuki error: {}", exception.message, exception)
        })

    private lateinit var job: Job

    override fun onLoad() {
        val webScreenshotTaker by ref<WebScreenshotTaker>()
        val port by GlobalContext.get().inject<Int>(named("port"))

        job = EventBus.subscribeAsync<RouteCallEvent>(scope) { event ->
            if (event hit CmdRouteRule.STATUS) {
                val bytes = webScreenshotTaker.takeFullScreenshot(
                    url = "http://eriix:%21%40Aa123@hostmachine:${port}/view/${event.botId}/${event.groupId}",
                    width = 1200,
                    quality = 100,
                    deviceScaleFactor = 3.0,
                    username = "eriix",
                    password = "!@Aa123"
                )
                val image = bytes.inputStream().use {
                    it.toExternalResource()
                }
                val roledBot = BotManage.getBot(event.botId)
                val bot = roledBot.refBot
                val group = bot.getGroup(event.groupId.toLong())!!
                image.use {
                    group.sendImage(it)
                }
            }
        }
    }

    override fun onUnload() {
        if (this::job.isInitialized) {
            job.cancel()
        }
    }
}