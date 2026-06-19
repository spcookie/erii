package uesugi.plugin.builtin.usage

import io.ktor.server.config.*
import org.koin.core.context.GlobalContext
import org.pf4j.Extension
import uesugi.common.toolkit.BrowserScraper
import uesugi.common.toolkit.BrowserScraperHolder
import uesugi.common.toolkit.ConfigHolder
import uesugi.core.component.usage.TokenUsageRepository
import uesugi.onebot.sdk.client.api.getGroupList
import uesugi.onebot.sdk.client.api.sendGroupMsg
import uesugi.onebot.sdk.message.buildMessage
import uesugi.plugin.builtin.Builtin
import uesugi.plugin.builtin.BuiltinExtension
import uesugi.routing.UsageViewCache
import uesugi.routing.UsageViewModel
import uesugi.routing.buildUsageViewModel
import uesugi.server.SystemConfigHolder
import uesugi.spi.*
import java.util.*

@Extension(points = [AgentExtension::class])
class Usage : CmdExtension<Unit, ArgParserHolder.Empty, Builtin>, BuiltinExtension {

    override val name: String
        get() = "builtin_usage"

    override val cmd: String
        get() = "usage"

    override fun onLoad(context: PluginContext) {
        val repository by GlobalContext.get().inject<TokenUsageRepository>()

        context.chain { meta ->
            val summary = repository.summary(botId = meta.botId, groupId = meta.groupId)
            renderUsage(
                meta,
                buildUsageViewModel(
                    summary = summary,
                    botId = meta.botId,
                    botName = meta.roledBot.role.name,
                    groupId = meta.groupId,
                    groupName = resolveGroupName(meta)
                )
            )
        }
    }
}

@Extension(points = [AgentExtension::class])
class UsageAll : CmdExtension<Unit, ArgParserHolder.Empty, Builtin>, BuiltinExtension {

    override val name: String
        get() = "builtin_usage_all"

    override val cmd: String
        get() = "usage-all"

    override fun onLoad(context: PluginContext) {
        val repository by GlobalContext.get().inject<TokenUsageRepository>()

        context.chain { meta ->
            val summary = repository.summary()
            renderUsage(meta, buildUsageViewModel(summary))
        }
    }
}

private suspend fun resolveGroupName(meta: Meta): String {
    return runCatching {
        meta.roledBot.refBot.getGroupList()
            .find { it.groupId.toString() == meta.groupId }
            ?.groupName
    }.getOrNull() ?: meta.groupId
}

private suspend fun renderUsage(meta: Meta, viewModel: UsageViewModel) {
    val browserScraper = BrowserScraperHolder.getInstance()
    val externalHost = ConfigHolder.getBrowserExternalHost()
    val port: Int = SystemConfigHolder.config.property("ktor.deployment.port").getAs()
    val username = SystemConfigHolder.config.property("security.username").getString()
    val password = SystemConfigHolder.config.property("security.password").getString()

    val id = UsageViewCache.put(viewModel)
    val bytes = try {
        browserScraper.takeFullScreenshot(
            url = "http://${externalHost}:${port}/usage/$id",
            width = 1280,
            height = 1500,
            quality = 100,
            type = BrowserScraper.ScreenshotType.JPEG,
            waitForNetworkIdle = true,
            username = username,
            password = password
        )
    } finally {
        UsageViewCache.take(id)
    }
    val base64 = Base64.getEncoder().encodeToString(bytes)
    meta.roledBot.refBot.sendGroupMsg(
        meta.groupId.toLong(),
        buildMessage { image("base64://$base64") }
    )
}
