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

    plugins.filterIsInstance<CmdPlugin<*, *>>()
        .forEach { plugin ->
            val cmdName = plugin.cmd
            CmdRuleRegister.addRule(cmdName)
        }

    plugins.forEach { plugin ->
    }

    val extension = DatabaseImpl()

    plugins.forEach { plugin ->
        val pluginDef = buildPluginDef(plugin)
        val mem = MemImpl()
        val kv = KvImpl(pluginDef)
        val blob = BlobImpl(pluginDef)
        val vector = VectorImpl(pluginDef)

        val server = ServerImpl(pluginDef)

        var context: PluginContext? = null

        single(named(pluginDef.name)) {
            plugin.apply {
                context = PluginContextImpl(
                    pluginDef,
                    mem,
                    kv,
                    blob,
                    vector,
                    extension,
                    get(),
                    get(),
                    get(named(HttpClientFactory.Type.NO_PROXY)),
                    server,
                    get(named(HttpClientFactory.Type.PROXY)),
                ).apply {
                    open()
                    plugin.onLoad(this)
                    ready()
                }
            }
        } onClose {
            mem.close()
            kv.close()
            blob.close()
            vector.close()
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
        if (plugin is CmdPlugin<*, *>) {
            add(CmdRouteKey(plugin.cmd))
        }
    }
    return PluginDefImpl(plugin.name, routeKeys)
}