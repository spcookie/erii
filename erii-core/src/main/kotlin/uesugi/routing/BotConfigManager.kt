package uesugi.routing

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import uesugi.common.BotManage
import uesugi.common.toolkit.ConfigHolder
import uesugi.core.bot.BotRoleManager
import uesugi.core.mcp.McpManager
import uesugi.core.rule.RuleManager

fun Routing.configureBotConfigManager() {
    authenticate("basic") {
        post("/api/config/refresh") {
            ConfigHolder.refresh()
            BotRoleManager.reload()
            RuleManager.reload()
            McpManager.refresh()

            val botConfigs = ConfigHolder.getOnebotBots()
            botConfigs.forEach { (key, config) ->
                val role = BotRoleManager.getRole(config.roleId) ?: return@forEach
                BotManage.refreshBotRole(key, role)
            }

            call.respond(mapOf("status" to "ok", "message" to "config cache refreshed"))
        }
    }
}
