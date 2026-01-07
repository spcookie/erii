package uesugi

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.globalEventChannel
import top.mrxiaom.overflow.BotBuilder
import uesugi.config.installIoc
import uesugi.core.BotAgent
import uesugi.core.history.GroupMessageEventListener
import uesugi.toolkit.logger

class BotProxy {
    companion object {
        @Volatile
        lateinit var currentBot: Bot
    }
}

private val log = BotProxy.logger()

fun main() {
    installIoc()
    installBot()
    runBotAgent()
}

private fun installBot() {
    runBlocking {
        val bot = BotBuilder.positive("ws://127.0.0.1:3001")
            .token("RmbZIX8PJ0E")
            .connect()
        bot ?: log.error("Bot is null")
        bot ?: return@runBlocking

        BotProxy.currentBot = bot

        bot.globalEventChannel()
            .exceptionHandler { log.error("Bot exception handler: {}", it.message, it) }
            .registerListenerHost(GroupMessageEventListener)
    }
}

private fun runBotAgent() {
    BotAgent.run()
}