package uesugi.core.plugin

import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.pf4j.*
import uesugi.config.HttpClientFactory
import uesugi.core.route.CmdRuleRegister
import uesugi.core.route.RouteRuleRegister
import uesugi.spi.*


class AgentPluginFactory : DefaultPluginFactory() {
    override fun create(pluginWrapper: PluginWrapper): Plugin {
        val plugin = super.create(pluginWrapper)
        plugin as AgentPlugin
        plugin.wrapper = pluginWrapper
        return plugin
    }
}

class AgentPluginManager : DefaultPluginManager() {
    override fun createPluginFactory(): PluginFactory {
        return AgentPluginFactory()
    }
}

fun pluginModule() = module(createdAtStart = true) {
    val pluginManager = AgentPluginManager()

    pluginManager.loadPlugins()
    pluginManager.startPlugins()

    val plugins = pluginManager.getExtensions(AgentExtension::class.java)


    plugins.filterIsInstance<RouteExtension>()
        .forEach { plugin ->
            val (name, description) = plugin.matcher
            RouteRuleRegister.addRule(name, description)
        }

    plugins.filterIsInstance<CmdExtension<*, *>>()
        .forEach { plugin ->
            val cmdName = plugin.cmd
            CmdRuleRegister.addRule(cmdName)
        }

    val database = DatabaseImpl()

    plugins.forEach { plugin ->
        val pluginDef = buildPluginDef(plugin)
        val mem = MemImpl()
        val kv = KvImpl(pluginDef)
        val blob = BlobImpl(pluginDef)
        val vector = VectorImpl(pluginDef)

        val config = ConfigImpl(plugin)

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
                    config,
                    database,
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

fun buildPluginDef(plugin: AgentExtension): PluginDef {
    val routeKeys = buildList {
        if (plugin is RouteExtension) {
            add(LLMRouteKey(plugin.matcher.first))
        }
        if (plugin is CmdExtension<*, *>) {
            add(CmdRouteKey(plugin.cmd))
        }
    }
    return PluginDefImpl(plugin.name, routeKeys)
}