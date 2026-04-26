package uesugi.plugin.animal

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import uesugi.common.BotManage
import uesugi.common.toolkit.BrowserScraper
import uesugi.common.toolkit.BrowserScraperHolder
import uesugi.common.toolkit.ConfigHolder
import uesugi.plugin.animal.service.AnimalService
import uesugi.plugin.animal.store.AnimalStore
import uesugi.spi.Meta
import uesugi.spi.getGroup

object AnimalContextFactory {

    fun createFromMeta(
        meta: Meta,
        store: AnimalStore,
        service: AnimalService,
        serverPort: Int,
        serverBasePath: String
    ): AnimalContext {
        val botId = meta.botId
        val configKey = BotManage.getConfigKey(botId)
        val botConfig = ConfigHolder.getOnebotBots()[configKey]

        val serverHost = botConfig?.serverHost ?: "hostmachine"
        val externalHost = botConfig?.externalHost ?: serverHost

        val userId = meta.senderId?.toLongOrNull() ?: 0L
        val senderNick = meta.senderId ?: "User"

        return AnimalContext(
            store = store,
            service = service,
            groupId = meta.groupId,
            senderId = userId,
            senderNick = senderNick,
            sendMessage = { msg ->
                runBlocking {
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
            serverUrl = "http://${externalHost}:${serverPort}${serverBasePath}",
            takeScreenshot = { url ->
                runCatching {
                    val playwrightUrl = url.replace("http://${externalHost}", "http://${serverHost}")
                    BrowserScraperHolder.getInstance().takeFullScreenshot(
                        url = playwrightUrl,
                        width = 100,
                        height = 30,
                        quality = 100,
                        type = BrowserScraper.ScreenshotType.JPEG
                    )
                }.getOrNull()
            }
        )
    }
}
