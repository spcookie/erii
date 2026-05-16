package uesugi.common

import net.mamoe.mirai.Bot
import uesugi.common.IBotManage.RoledBot
import uesugi.common.data.EmotionalTendencies
import uesugi.common.toolkit.logger
import java.util.concurrent.ConcurrentHashMap

interface IBotManage {

    data class RoledBot(
        val refBot: Bot,
        val role: BotRole,
    )

    fun registerBot(configKey: String, bot: Bot, role: BotRole)
    fun getBot(botId: String): RoledBot
    fun getBotByConfigKey(configKey: String): RoledBot
    fun getConfigKey(botId: String): String
    fun getAllBots(): Collection<RoledBot>
    fun getAllBotIds(): Set<String>
    fun refreshBotRole(configKey: String, role: BotRole)
}

object BotManage : IBotManage {

    private val bots = ConcurrentHashMap<String, RoledBot>()
    private val configKeys = ConcurrentHashMap<String, String>()
    private val botKeys = ConcurrentHashMap<String, String>()

    private val log = logger()

    override fun registerBot(configKey: String, bot: Bot, role: BotRole) {
        val botId = bot.id.toString()
        bots[botId] = RoledBot(bot, role)
        configKeys[configKey] = botId
        botKeys[botId] = configKey
        log.info("Robot registered: botId=$botId")
    }

    override fun getBot(botId: String): RoledBot {
        return bots.getValue(botId)
    }

    override fun getBotByConfigKey(configKey: String): RoledBot {
        val botId = configKeys.getValue(configKey)
        return bots.getValue(botId)
    }

    override fun getConfigKey(botId: String): String {
        return botKeys.getValue(botId)
    }

    override fun getAllBots(): Collection<RoledBot> {
        return bots.values
    }

    override fun getAllBotIds(): Set<String> {
        return bots.keys
    }

    override fun refreshBotRole(configKey: String, role: BotRole) {
        val botId = configKeys[configKey] ?: return
        bots[botId]?.let {
            bots[botId] = it.copy(role = role)
        }
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