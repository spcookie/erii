package uesugi.core.plugin

import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.pf4j.*
import uesugi.config.HttpClientFactory
import uesugi.core.route.CmdRuleRegister
import uesugi.core.route.RouteRuleRegister
import uesugi.spi.*
import java.util.*


class AgentPluginFactory : DefaultPluginFactory() {
    override fun create(pluginWrapper: PluginWrapper): Plugin {
        return super.create(pluginWrapper)
    }

}

class AgentPluginManager : DefaultPluginManager() {
    override fun createPluginFactory(): PluginFactory? {
        return super.createPluginFactory()
    }

}

fun pluginModule() = module(createdAtStart = true) {

    DefaultPluginManager()

    val plugins = ServiceLoader.load(AgentExtension::class.java).toList()

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