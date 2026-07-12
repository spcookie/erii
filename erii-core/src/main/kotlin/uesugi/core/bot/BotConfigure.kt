package uesugi.core.bot

import kotlinx.coroutines.runBlocking
import uesugi.core.agent.BotAgent

fun configureConnectBots() {
    BotRoleManager.loadRoles()
    OneBotConnectionManager.start()
}

fun configureBotAgent() = BotAgent.run()

fun disconnectBots() = runBlocking { OneBotConnectionManager.disconnectAll() }
