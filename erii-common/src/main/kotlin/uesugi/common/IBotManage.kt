package uesugi.common

import net.mamoe.mirai.Bot

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