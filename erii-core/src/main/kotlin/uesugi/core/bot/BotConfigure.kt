package uesugi.core.bot

import kotlinx.coroutines.*
import net.mamoe.mirai.Bot
import net.mamoe.mirai.event.globalEventChannel
import top.mrxiaom.overflow.BotBuilder
import uesugi.LOG
import uesugi.common.BotManage
import uesugi.common.ConfigHolder
import uesugi.core.agent.BotAgent
import uesugi.core.GroupMessageEventListener as MessageListener


fun configureConnectBots() {
    // 加载 BotRole 配置
    BotRoleManager.loadRoles()

    val botConfigs = ConfigHolder.getOnebotBots()

    if (botConfigs.isEmpty()) {
        LOG.warn("未配置任何机器人")
        return
    }
    LOG.info("准备连接 ${botConfigs.size} 个机器人")

    CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("connect-bots"))
        .launch {
            botConfigs.forEach { (key, config) ->
                launch {
                    val role = BotRoleManager.getRole(config.roleId)
                        ?: BotRoleManager.getDefaultRole()
                    LOG.info("正在连接机器人 $key，使用角色: ${role.name}")

                    var bot: Bot? = null
                    try {
                        bot = BotBuilder.positive(config.ws)
                            .token(config.token)
                            .connect()
                    } catch (e: Exception) {
                        LOG.error("机器人 $key，接失败: ${e.message}")
                    }

                    if (bot != null) {
                        BotManage.registerBot(key, bot, role)
                        // 为每个 bot 创建独立的监听器实例
                        val listener = MessageListener(bot.id.toString(), role.name)
                        bot.globalEventChannel()
                            .exceptionHandler { LOG.error("Bot $key exception: {}", it.message) }
                            .registerListenerHost(listener)
                        LOG.info("机器人 $key (${role.name}) 已连接: ${bot.id}")
                    } else {
                        LOG.error("机器人 $key 连接失败")
                    }
                }
            }
        }
}

fun configureBotAgent() = BotAgent.run()