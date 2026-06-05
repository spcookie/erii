package uesugi.core.bot

import kotlinx.coroutines.*
import uesugi.LOG
import uesugi.common.BotManage
import uesugi.common.toolkit.ConfigHolder
import uesugi.core.GroupMessageEventListener
import uesugi.core.agent.BotAgent
import uesugi.onebot.core.config.OneBotConfig
import uesugi.onebot.core.pipeline.LoggingMiddleware
import uesugi.onebot.sdk.client.OneBotClient
import uesugi.onebot.sdk.client.api.getLoginInfo

private val botScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineName("connect-bots"))


fun configureConnectBots() {
    BotRoleManager.loadRoles()

    val botConfigs = ConfigHolder.getOnebotBots()

    if (botConfigs.isEmpty()) {
        LOG.warn("No robots configured")
        return
    }
    LOG.info("Prepare to connect ${botConfigs.size} robots")

    botScope.launch {
            botConfigs.forEach { (key, config) ->
                val role = BotRoleManager.getRole(config.roleId)
                    ?: BotRoleManager.getDefaultRole()
                LOG.info("Connecting robot $key, using role: ${role.name}")

                try {
                    val onebotConfig = OneBotConfig(
                        wsForwardClientEnable = true,
                        wsForwardClientUseUniversal = true,
                        wsForwardClientUrl = config.ws,
                        accessToken = config.token
                    )
                    val client = OneBotClient(onebotConfig)
                    client.use(LoggingMiddleware(LOG))
                    client.start()

                    val selfId = try {
                        val loginInfo = client.getLoginInfo()
                        loginInfo.userId.toString().also {
                            LOG.info("Robot $key selfId resolved via get_login_info: $it")
                        }
                    } catch (_: Exception) {
                        config.selfId ?: run {
                            LOG.error("Robot $key: get_login_info failed and config.selfId is not set, skipping")
                            return@forEach
                        }
                    }

                    BotManage.registerBot(key, client, selfId, role)

                    val listener = GroupMessageEventListener(selfId, role.name, key)
                    listener.register(client)

                    LOG.info("Robot $key (${role.name}) has been connected: $selfId")
                } catch (e: Exception) {
                    LOG.error("Robot $key, failed to connect: ${e.message}")
                }
            }
        }
}

fun configureBotAgent() = BotAgent.run()

fun disconnectBots() {
    LOG.info("Disconnecting all bots...")
    BotManage.closeAll()
    botScope.cancel()
    LOG.info("All bots disconnected")
}
