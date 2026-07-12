package uesugi.common

import uesugi.common.RefreshManager.refreshAll


/**
 * 配置刷新的聚合入口(依赖倒置)。
 *
 * 各子系统(多位于 erii-core,如角色/规则/MCP/OneBot 连接管理器)在启动时把自己的刷新动作
 * 注册进来;`/api/config/refresh` 端点只需调用 [refreshAll],按注册顺序依次执行并收集结果。
 * 这样 common 无需反向依赖 core 即可承载统一的刷新入口。
 */
object RefreshManager {

    private val refreshers = LinkedHashMap<String, suspend () -> Any?>()

    /** 注册一个刷新动作。name 作为结果与响应中的 key;重复 name 覆盖。注册顺序即执行顺序。 */
    fun register(name: String, action: suspend () -> Any?) {
        refreshers[name] = action
    }

    /** 按注册顺序执行全部刷新动作,返回 name -> 结果。 */
    suspend fun refreshAll(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for ((name, action) in refreshers) {
            out[name] = action()
        }
        return out
    }
}
