package uesugi.plugin.builtin.status

import io.ktor.server.config.*
import org.pf4j.Extension
import uesugi.common.toolkit.BrowserScraper
import uesugi.common.toolkit.BrowserScraperHolder
import uesugi.common.toolkit.ConfigHolder
import uesugi.onebot.sdk.client.api.sendGroupMsg
import uesugi.onebot.sdk.message.buildMessage
import uesugi.plugin.builtin.Builtin
import uesugi.plugin.builtin.BuiltinExtension
import uesugi.server.SystemConfigHolder
import uesugi.spi.AgentExtension
import uesugi.spi.ArgParserHolder
import uesugi.spi.CmdExtension
import uesugi.spi.PluginContext
import java.util.*

@Extension(points = [AgentExtension::class])
class RenderingStatus : CmdExtension<Unit, ArgParserHolder.Empty, Builtin>, BuiltinExtension {

    override val name: String
        get() = "builtin_rendering"

    override fun onLoad(context: PluginContext) {
        val browserScraper = BrowserScraperHolder.getInstance()
        val externalHost = ConfigHolder.getBrowserExternalHost()

        val port: Int = SystemConfigHolder.config
            .property("ktor.deployment.port")
            .getAs()

        val username = SystemConfigHolder.config.property("security.username").getString()
        val password = SystemConfigHolder.config.property("security.password").getString()

        context.chain { meta ->
            val bytes = browserScraper.takeFullScreenshot(
                url = "http://${externalHost}:${port}/view/${meta.botId}/${meta.groupId}",
                width = 1200,
                quality = 100,
                type = BrowserScraper.ScreenshotType.JPEG,
                waitForNetworkIdle = true,
                username = username,
                password = password
            )
            val base64 = Base64.getEncoder().encodeToString(bytes)
            meta.roledBot.refBot.sendGroupMsg(
                meta.groupId.toLong(),
                buildMessage { image("base64://$base64") }
            )
        }
    }

    override val cmd: String
        get() = "status"
}
