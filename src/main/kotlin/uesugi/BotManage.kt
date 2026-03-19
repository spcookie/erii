package uesugi

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.globalEventChannel
import top.mrxiaom.overflow.BotBuilder
import uesugi.config.ConfigHolder
import uesugi.core.BotRole
import uesugi.core.Erii
import uesugi.core.agent.BotAgent
import uesugi.toolkit.logger
import java.util.concurrent.ConcurrentHashMap

val DEBUG_GROUP_ID: String? = ConfigHolder.getDebugGroupId()

val ENABLE_GROUPS: List<String> = ConfigHolder.getEnableGroups()

val MESSAGE_REDIRECT_GROUP_MAP: Map<String, String> = ConfigHolder.getMessageRedirectMap()

object BotManage {

    data class RoledBot(
        val refBot: Bot,
        val role: BotRole,
    )

    private val bots = ConcurrentHashMap<String, RoledBot>()

    private val log = logger()

    fun registerBot(bot: Bot, role: BotRole) {
        val botId = bot.id.toString()
        bots[botId] = RoledBot(bot, role)
        log.info("机器人已注册: botId=$botId")
    }

    fun getBot(botId: String): RoledBot {
        return bots.getValue(botId)
    }

    fun getAllBots(): Collection<RoledBot> {
        return bots.values
    }

    fun getAllBotIds(): Set<String> {
        return bots.keys
    }

}

private val log = BotManage.logger()

fun configureConnectBots() {
    runBlocking {
        val erii = BotBuilder.positive(ConfigHolder.getNapcatWs())
            .token(ConfigHolder.getNapcatToken())
            .connect()

        if (erii == null) {
            log.error("机器人 erii 连接失败")
        } else {
            BotManage.registerBot(erii, Erii)

            erii.globalEventChannel()
                .exceptionHandler { log.error("Bot exception handler: {}", it.message, it) }
                .registerListenerHost(uesugi.core.GroupMessageEventListener)

            log.info("机器人 erii 已连接: ${erii.id}")
        }
    }
}

fun configureBotAgent() = BotAgent.run()