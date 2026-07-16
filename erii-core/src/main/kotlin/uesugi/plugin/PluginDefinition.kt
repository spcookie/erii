package uesugi.plugin

import uesugi.spi.*

fun buildPluginDef(plugin: AgentExtension<*>): PluginDef {
    val routeKeys = buildList {
        if (plugin is RouteExtension) {
            add(LLMRouteKey(plugin.matcher.first))
        }
        if (plugin is CmdExtension<*, *, *>) {
            add(CmdRouteKey(plugin.cmd))
        }
    }
    return PluginDefImpl(plugin.name, routeKeys)
}
