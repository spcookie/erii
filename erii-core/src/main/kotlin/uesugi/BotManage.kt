package uesugi

import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.globalEventChannel
import top.mrxiaom.overflow.BotBuilder
import uesugi.common.BotRole
import uesugi.common.ConfigHolder
import uesugi.common.IBotManage
import uesugi.common.IBotManage.RoledBot
import uesugi.common.logger
import uesugi.core.BotRoleManager
import uesugi.core.agent.BotAgent
import java.util.concurrent.ConcurrentHashMap
import uesugi.core.GroupMessageEventListener as MessageListener

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

private val log = BotManage.logger()

fun configureConnectBots() {
    // 加载 BotRole 配置
    BotRoleManager.loadRoles()

    val botConfigs = ConfigHolder.getNapcatBots()

    if (botConfigs.isEmpty()) {
        log.warn("未配置任何机器人")
        return
    }
    log.info("准备连接 ${botConfigs.size} 个机器人")

    CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("connect-bots"))
        .launch {
            botConfigs.forEach { (key, config) ->
                launch {
                    val role = BotRoleManager.getRole(config.roleId)
                        ?: BotRoleManager.getDefaultRole()
                    log.info("正在连接机器人 $key，使用角色: ${role.name}")

                    var bot: Bot? = null
                    try {
                        bot = BotBuilder.positive(config.ws)
                            .token(config.token)
                            .connect()
                    } catch (e: Exception) {
                        log.error("机器人 $key，接失败: ${e.message}")
                    }

                    if (bot != null) {
                        BotManage.registerBot(bot, role)
                        // 为每个 bot 创建独立的监听器实例
                        val listener = MessageListener(bot.id.toString(), role.name)
                        bot.globalEventChannel()
                            .exceptionHandler { log.error("Bot $key exception: {}", it.message) }
                            .registerListenerHost(listener)
                        log.info("机器人 $key (${role.name}) 已连接: ${bot.id}")
                    } else {
                        log.error("机器人 $key 连接失败")
                    }
                }
            }
        }
}

fun configureBotAgent() = BotAgent.run()