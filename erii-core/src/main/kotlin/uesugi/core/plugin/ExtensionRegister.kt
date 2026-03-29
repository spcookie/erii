package uesugi.core.plugin

import uesugi.spi.AgentExtension
import uesugi.spi.CmdExtension
import uesugi.spi.PassiveExtension
import uesugi.spi.RouteExtension

object ExtensionRegister {

    private val extensions = mutableListOf<AgentExtension<*>>()
    private val plugins = mutableMapOf<String, MutableList<AgentExtension<*>>>()

    fun add(pluginId: String?, extension: AgentExtension<*>) {
        extensions.add(extension)
        if (pluginId != null) {
            plugins.getOrPut(pluginId) { mutableListOf() }.add(extension)
        }
    }

    fun clear() {
        extensions.clear()
    }

    fun getAllPlugins(): Map<String, List<AgentExtension<*>>> {
        return plugins
    }

    fun getExtensions(pluginId: String): List<AgentExtension<*>> {
        return plugins.getOrDefault(pluginId, mutableListOf())
    }

    fun getAllExtensions(): List<AgentExtension<*>> {
        return extensions
    }

    fun getCmdExtensions(): List<AgentExtension<*>> {
        return extensions.filterIsInstance<CmdExtension<*, *, *>>()
    }

    fun getRouteExtensions(): List<AgentExtension<*>> {
        return extensions.filterIsInstance<RouteExtension<*>>()
    }

    fun getPassiveExtensions(): List<AgentExtension<*>> {
        return extensions.filterIsInstance<PassiveExtension<*>>()
    }

}