package uesugi.plugin

import uesugi.spi.*

fun buildPluginDef(plugin: AgentExtension<*>): PluginDef =
    PluginDefImpl(plugin.name) {
        buildRouteKeys(plugin)
    }

private fun buildRouteKeys(plugin: AgentExtension<*>): List<RouteKey> =
    buildList {
        if (plugin is RouteExtension) {
            add(LLMRouteKey(plugin.matcher.first))
        }
        if (plugin is CmdExtension<*, *, *>) {
            add(CmdRouteKey(plugin.cmd))
        }
    }
