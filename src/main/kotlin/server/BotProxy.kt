package uesugi.server

import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.globalEventChannel
import org.h2.tools.Server
import top.mrxiaom.overflow.BotBuilder
import uesugi.core.BotAgent
import uesugi.core.BotRole
import uesugi.core.Erii
import uesugi.core.GroupMessageEventListener
import uesugi.toolkit.logger
import java.util.concurrent.ConcurrentHashMap

object BotProxy {

    data class RoledBot(
        val bot: Bot,
        val role: BotRole,
    )

    private val bots = ConcurrentHashMap<String, RoledBot>()

    private val log = logger()

    fun registerBot(bot: Bot, role: BotRole) {
        val botId = bot.id.toString()
        bots[botId] = RoledBot(bot, role)
        log.info("机器人已注册: botId=$botId")
    }

    fun getBot(botId: String): RoledBot? {
        return bots[botId]
    }

    fun getAllBots(): Collection<RoledBot> {
        return bots.values
    }

    fun getAllBotIds(): Set<String> {
        return bots.keys
    }

}

private val log = BotProxy.logger()

val DEBUG_GROUP_ID: String? = "474270623"

fun configureConnectBots() {
    runBlocking {
        // 连接第一个机器人
        val erii = BotBuilder.positive("ws://127.0.0.1:3001")
            .token("hG8dQqGk6jGC")
            .connect()

        if (erii == null) {
            log.error("机器人 erii 连接失败")
        } else {
            BotProxy.registerBot(erii, Erii)

            erii.globalEventChannel()
                .exceptionHandler { log.error("Bot exception handler: {}", it.message, it) }
                .registerListenerHost(GroupMessageEventListener)

            log.info("机器人 erii 已连接: ${erii.id}")
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

fun configureBotAgent() = BotAgent.run()

fun configureH2Console() {
    val enabled = System.getProperty("h2.console.enabled", "true").toBoolean()
    if (!enabled) return
    val h2Console = Server.createWebServer("-web", "-webPort", "8082")
    h2Console.start()
    log.info("H2 console started at http://localhost:8082")
}