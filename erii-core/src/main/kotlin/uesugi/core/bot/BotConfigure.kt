package uesugi.core.bot

import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.globalEventChannel
import top.mrxiaom.overflow.BotBuilder
import uesugi.LOG
import uesugi.common.BotManage
import uesugi.common.toolkit.ConfigHolder
import uesugi.core.agent.BotAgent
import uesugi.core.GroupMessageEventListener as MessageListener


fun configureConnectBots() {
    // 加载 BotRole 配置
    BotRoleManager.loadRoles()

    val botConfigs = ConfigHolder.getOnebotBots()

    if (botConfigs.isEmpty()) {
        LOG.warn("No robots configured")
        return
    }
    LOG.info("Prepare to connect ${botConfigs.size} robots")

    CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("connect-bots"))
        .launch {
            botConfigs.forEach { (key, config) ->
                val role = BotRoleManager.getRole(config.roleId)
                    ?: BotRoleManager.getDefaultRole()
                LOG.info("Connecting robot $key, using role: ${role.name}")

                var bot: Bot? = null
                try {
                    bot = BotBuilder.positive(config.ws)
                        .token(config.token)
                        .connect()
                } catch (e: Exception) {
                    LOG.error("Robot $key, failed to connect: ${e.message}")
                }

                if (bot != null) {
                    BotManage.registerBot(key, bot, role)
                    // 为每个 bot 创建独立的监听器实例
                    val listener = MessageListener(bot.id.toString(), role.name)
                    bot.globalEventChannel()
                        .exceptionHandler { LOG.error("Bot $key exception: {}", it.message) }
                        .registerListenerHost(listener)
                    LOG.info("Robot $key (${role.name}) has been connected: ${bot.id}")
                } else {
                    LOG.error("Robot $key connection failed")
                }
            }
        }
}

fun configureBotAgent() = BotAgent.run()