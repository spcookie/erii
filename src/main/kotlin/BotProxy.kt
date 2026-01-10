package uesugi

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.globalEventChannel
import org.h2.tools.Server
import top.mrxiaom.overflow.BotBuilder
import uesugi.config.installIOC
import uesugi.core.BotAgent
import uesugi.core.GroupMessageEventListener
import uesugi.core.volition.InterruptionMode
import uesugi.core.volition.ProactiveSpeakEvent
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import java.util.concurrent.ConcurrentHashMap

object BotProxy {
    private val bots = ConcurrentHashMap<String, Bot>()

    private val log = logger()

    fun registerBot(bot: Bot) {
        val botId = bot.id.toString()
        bots[botId] = bot
        log.info("机器人已注册: botId=$botId")
    }

    fun getBot(botId: String): Bot? {
        return bots[botId]
    }

    fun getAllBots(): Collection<Bot> {
        return bots.values
    }

    fun getAllBotIds(): Set<String> {
        return bots.keys
    }

}

private val log = BotProxy.logger()

val DEBUG_GROUP_ID: String? = "474270623"

fun main() {
    installIOC()
    connectBots()
    runBotAgent()
    startH2Console()
    
    while (true) {
        val readLine = readlnOrNull()
        if (readLine == "1") {
            log.info("触发聊天")
            val firstBot = BotProxy.getAllBots().firstOrNull()
            if (firstBot != null) {
                EventBus.postAsync(
                    ProactiveSpeakEvent(
                        botMark = firstBot.id.toString(),
                        groupId = "",
                        impulse = 0.0,
                        interruptionMode = InterruptionMode.Interrupt,
                    )
                )
            }
        }
    }
}

private fun connectBots() {
    runBlocking {
        // 连接第一个机器人
        val bot1 = BotBuilder.positive("ws://127.0.0.1:3001")
            .token("hG8dQqGk6jGC")
            .connect()

        if (bot1 == null) {
            log.error("机器人1连接失败")
        } else {
            BotProxy.registerBot(bot1)

            bot1.globalEventChannel()
                .exceptionHandler { log.error("Bot exception handler: {}", it.message, it) }
                .registerListenerHost(GroupMessageEventListener)

            log.info("机器人1已连接: ${bot1.id}")
        }

        // 可以在这里添加更多机器人连接
        // val bot2 = BotBuilder.positive("ws://127.0.0.1:3002")
        //     .token("another_token")
        //     .connect()
        // if (bot2 != null) {
        //     BotProxy.registerBot(bot2)
        //     bot2.globalEventChannel()
        //         .exceptionHandler { log.error("Bot exception handler: {}", it.message, it) }
        //         .registerListenerHost(GroupMessageEventListener)
        // }
    }
}

private fun runBotAgent() {
    BotAgent.run()
}

fun startH2Console() {
    val enabled = System.getProperty("h2.console.enabled", "true").toBoolean()
    if (!enabled) return
    val h2Console = Server.createWebServer("-web", "-webPort", "8082")
    h2Console.start()
    log.info("H2 console started at http://localhost:8082")
}