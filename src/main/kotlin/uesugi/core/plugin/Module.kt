package uesugi.core.plugin

import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import uesugi.config.HttpClientFactory
import uesugi.core.route.CmdRuleRegister
import uesugi.core.route.RouteRuleRegister
import java.util.*

fun pluginModule() = module(createdAtStart = true) {
    val plugins = ServiceLoader.load(Plugin::class.java).toList()

    plugins.filterIsInstance<RoutePlugin>()
        .forEach { plugin ->
            val (name, description) = plugin.matcher
            RouteRuleRegister.addRule(name, description)
        }

    plugins.filterIsInstance<CmdPlugin>()
        .forEach { plugin ->
            val argParser = plugin.argParser
            CmdRuleRegister.addRule(argParser.programName, argParser)
        }

    val mem = MemImpl()
    val extension = DatabaseImpl()

    plugins.forEach { plugin ->
        val pluginDef = buildPluginDef(plugin)
        val kv = KvImpl(pluginDef)
        val blob = BlobImpl(pluginDef)

        var context: PluginContext? = null

        single(named(pluginDef.name)) {
            context = PluginContextImpl(
                pluginDef,
                mem,
                kv,
                blob,
                extension,
                get(),
                get(),
                get(named(HttpClientFactory.Type.NO_PROXY)),
                get(named(HttpClientFactory.Type.PROXY)),
            ).apply { open() }
            plugin.apply { onLoad(context) }
        } onClose {
            context?.close()
            it?.onUnload()
        }
    }

}

fun buildPluginDef(plugin: Plugin): PluginDef {
    val routeKeys = buildList {
        if (plugin is RoutePlugin) {
            add(LLMRouteKey(plugin.matcher.first))
        }
        if (plugin is CmdPlugin) {
            add(CmdRouteKey(plugin.argParser.programName))
        }
    }
    return PluginDefImpl(plugin.name, routeKeys)
}