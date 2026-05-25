package uesugi.spi.annotation

import uesugi.spi.AgentPlugin
import uesugi.spi.PluginContext

internal object LifecycleRegistry {
    private val onStartHandlers = mutableListOf<suspend (PluginContext) -> Unit>()
    private val onStopHandlers = mutableListOf<suspend () -> Unit>()

    fun onStart(handler: suspend (PluginContext) -> Unit) = onStartHandlers.add(handler)
    fun onStop(handler: suspend () -> Unit) = onStopHandlers.add(handler)

    fun start(plugin: AgentPlugin) {
        // PluginContext 在 start() 时不可用，留空或后续扩展
    }

    fun stop(plugin: AgentPlugin) {
        // 遍历执行 onStopHandlers
    }
}
