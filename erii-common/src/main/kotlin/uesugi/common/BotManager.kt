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

sealed interface BotRole {
    val id: String
    val name: String
    fun personality(botId: String): String
    val character: String
    val emoticon: EmotionalTendencies
}

/**
 * 从配置文件加载的 BotRole 实现类
 */
data class ConfigBotRole(
    override val id: String,
    override val name: String,
    private val personalityTemplate: String,
    override val character: String,
    override val emoticon: EmotionalTendencies
) : BotRole {
    override fun personality(botId: String): String = personalityTemplate.replace("{{botId}}", botId)
}