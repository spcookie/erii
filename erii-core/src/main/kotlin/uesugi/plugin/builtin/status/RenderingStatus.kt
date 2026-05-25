package uesugi.plugin.builtin.status

import io.ktor.server.config.*
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.pf4j.Extension
import uesugi.common.toolkit.BrowserScraper
import uesugi.common.toolkit.BrowserScraperHolder
import uesugi.plugin.builtin.Builtin
import uesugi.plugin.builtin.BuiltinExtension
import uesugi.server.SystemConfigHolder
import uesugi.spi.AgentExtension
import uesugi.spi.ArgParserHolder
import uesugi.spi.CmdExtension
import uesugi.spi.PluginContext

@Extension(points = [AgentExtension::class])
class RenderingStatus : CmdExtension<Unit, ArgParserHolder.Empty, Builtin>, BuiltinExtension {

    override val name: String
        get() = "builtin_rendering"

    override fun onLoad(context: PluginContext) {
        val browserScraper = BrowserScraperHolder.getInstance()
        val statusHost = SystemConfigHolder.config
            .propertyOrNull("browser.status-host")
            ?.getString()
            ?: "hostmachine"

        val port: Int = SystemConfigHolder.config
            .property("ktor.deployment.port")
            .getAs()

        val username = SystemConfigHolder.config.property("security.username").getString()
        val password = SystemConfigHolder.config.property("security.password").getString()

        context.chain { meta ->
            val bytes = browserScraper.takeFullScreenshot(
                url = "http://${statusHost}:${port}/view/${meta.botId}/${meta.groupId}",
                width = 1200,
                quality = 100,
                type = BrowserScraper.ScreenshotType.JPEG,
                username = username,
                password = password
            )
            val image = bytes.inputStream().use {
                it.toExternalResource()
            }
            val group = meta.roledBot.refBot.getGroupOrFail(meta.groupId.toLong())
            image.use {
                group.sendImage(it)
            }
        }
    }

    override val cmd: String
        get() = "status"
}
