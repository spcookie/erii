package uesugi.common

import net.mamoe.mirai.Bot
import uesugi.common.IBotManage.RoledBot
import java.util.concurrent.ConcurrentHashMap

interface IBotManage {

    data class RoledBot(
        val refBot: Bot,
        val role: BotRole,
    )

    fun registerBot(bot: Bot, role: BotRole)
    fun getBot(botId: String): RoledBot
    fun getAllBots(): Collection<RoledBot>
    fun getAllBotIds(): Set<String>
}

object BotManage : IBotManage {

    private val bots = ConcurrentHashMap<String, RoledBot>()

    private val log = logger()

    override fun registerBot(bot: Bot, role: BotRole) {
        val botId = bot.id.toString()
        bots[botId] = RoledBot(bot, role)
        log.info("机器人已注册: botId=$botId")
    }

    override fun getBot(botId: String): RoledBot {
        return bots.getValue(botId)
    }

    override fun getAllBots(): Collection<RoledBot> {
        return bots.values
    }

    override fun getAllBotIds(): Set<String> {
        return bots.keys
    }

}