package uesugi.core.plugin.builtin.status

import io.ktor.server.config.*
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.pf4j.Extension
import uesugi.common.ref
import uesugi.core.component.browser.WebScreenshotTaker
import uesugi.core.plugin.builtin.Builtin
import uesugi.core.plugin.builtin.BuiltinExtension
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
        val webScreenshotTaker by ref<WebScreenshotTaker>()
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
            val bytes = webScreenshotTaker.takeFullScreenshot(
                url = "http://${statusHost}:${port}/view/${meta.botId}/${meta.groupId}",
                width = 1200,
                quality = 100,
                deviceScaleFactor = 3.0,
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