package uesugi.plugins.system.status

import com.google.auto.service.AutoService
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import uesugi.core.plugin.*
import uesugi.toolkit.ref

@AutoService(Plugin::class)
class RenderingStatus : CmdPlugin<Unit, ArgParserHolder.Empty>, ClassNameMixin {

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