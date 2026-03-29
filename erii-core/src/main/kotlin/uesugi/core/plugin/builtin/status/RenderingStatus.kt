package uesugi.core.plugin.builtin.status

import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import org.pf4j.Extension
import uesugi.common.ref
import uesugi.core.plugin.builtin.Builtin
import uesugi.core.plugin.builtin.BuiltinExtension
import uesugi.spi.AgentExtension
import uesugi.spi.ArgParserHolder
import uesugi.spi.CmdExtension
import uesugi.spi.PluginContext
import uesugi.toolkit.WebScreenshotTaker

@Extension(points = [AgentExtension::class])
class RenderingStatus : CmdExtension<Unit, ArgParserHolder.Empty, Builtin>, BuiltinExtension {

    override val name: String
        get() = "builtin_rendering"

    override fun onLoad(context: PluginContext) {
        val webScreenshotTaker by ref<WebScreenshotTaker>()
        val port by GlobalContext.get().inject<Int>(named("port"))

        context.chain { meta ->
            val bytes = webScreenshotTaker.takeFullScreenshot(
                url = "http://hostmachine:${port}/view/${meta.botId}/${meta.groupId}",
                width = 1200,
                quality = 100,
                deviceScaleFactor = 3.0,
                username = "eriix",
                password = "!@Aa123"
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