package uesugi.config

import uesugi.common.RefreshManager
import uesugi.common.toolkit.ConfigHolder
import uesugi.core.bot.BotRoleManager
import uesugi.core.bot.OneBotConnectionManager
import uesugi.core.mcp.McpManager
import uesugi.core.rule.RuleManager

/**
 * 向 [RefreshManager] 注册各子系统的刷新动作(依赖倒置的 core 侧)。
 * 在应用启动时调用一次即可。注册顺序即执行顺序:先整体配置/角色/规则/MCP,再 diff OneBot 连接。
 */
fun registerRefreshers() {
    RefreshManager.register("reloaded") {
        ConfigHolder.refresh()
        BotRoleManager.reload()
        RuleManager.reload()
        McpManager.refresh()
        mapOf(
            "config" to true,
            "roles" to BotRoleManager.getAllRoles().size,
            "rules" to RuleManager.getAllRules().size,
            "mcp" to McpManager.count(),
        )
    }
    RefreshManager.register("bots") {
        val r = OneBotConnectionManager.refresh()
        mapOf(
            "added" to r.added,
            "removed" to r.removed,
            "reconnected" to r.reconnected,
            "roleUpdated" to r.roleUpdated,
            "failed" to r.failed,
        )
    }
}
