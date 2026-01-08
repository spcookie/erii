package uesugi

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.globalEventChannel
import top.mrxiaom.overflow.BotBuilder
import uesugi.BotProxy.Companion.currentBot
import uesugi.config.installIoc
import uesugi.core.BotAgent
import uesugi.core.history.GroupMessageEventListener
import uesugi.core.volition.InterruptionMode
import uesugi.core.volition.ProactiveSpeakEvent
import uesugi.toolkit.EventBus
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
    connectBot()
    runBotAgent()
    while (true) {
        val readLine = readlnOrNull()
        if (readLine == "1") {
            log.info("触发聊天")
            EventBus.postAsync(ProactiveSpeakEvent(10.0, InterruptionMode.Interrupt))
        }
    }
}

private fun connectBot() {
    runBlocking {
        val bot = BotBuilder.positive("ws://127.0.0.1:3001")
            .token("hG8dQqGk6jGC")
            .connect()
        bot ?: log.error("Bot is null")
        bot ?: return@runBlocking

        currentBot = bot

        bot.globalEventChannel()
            .exceptionHandler { log.error("Bot exception handler: {}", it.message, it) }
            .registerListenerHost(GroupMessageEventListener)
    }
}

private fun runBotAgent() {
    BotAgent.run()
}