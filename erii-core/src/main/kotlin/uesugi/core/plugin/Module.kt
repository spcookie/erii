package uesugi.core.plugin

import org.koin.core.parameter.parametersOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.pf4j.*
import uesugi.LOG
import uesugi.config.HttpClientFactory
import uesugi.core.plugin.builtin.BuiltinExtension
import uesugi.core.route.CmdRuleRegister
import uesugi.core.route.RouteRuleRegister
import uesugi.spi.*


class AgentPluginFactory : DefaultPluginFactory() {
    override fun create(pluginWrapper: PluginWrapper): Plugin? {
        val plugin = super.create(pluginWrapper)
        if (plugin !is AgentPlugin) return null
        plugin.wrapper = pluginWrapper
        return plugin
    }
}

class AgentPluginManager : DefaultPluginManager() {
    override fun createPluginFactory(): PluginFactory {
        return AgentPluginFactory()
    }


    override fun createExtensionFactory(): ExtensionFactory {
        return SingletonExtensionFactory(this)
    }

    override fun createPluginLoader(): PluginLoader? {
        return super.createPluginLoader()
    }
}

fun pluginModule() = module(createdAtStart = true) {
    val pluginManager = AgentPluginManager()

    single { pluginManager } onClose {
        pluginManager.stopPlugins()
        pluginManager.unloadPlugins()
    }

    pluginManager.loadPlugins()
    pluginManager.startPlugins()

    LOG.info("Loaded ${pluginManager.startedPlugins.size} plugins")

    val extensions = buildList {
        val builtinExtensions = pluginManager.getExtensions(BuiltinExtension::class.java)
        builtinExtensions.forEach { ExtensionRegister.add(null, it) }
        val pluginExtensions = pluginManager.startedPlugins.flatMap { pluginWrapper ->
            runCatching {
                val extensions = pluginManager.getExtensions(AgentExtension::class.java, pluginWrapper.pluginId)
                extensions.forEach { ExtensionRegister.add(pluginWrapper.pluginId, it) }
                extensions
            }.onFailure {
                LOG.warn("Failed to get extensions for ${pluginWrapper.pluginId}", it)
            }.getOrDefault(emptyList())
        }
        addAll(builtinExtensions)
        addAll(pluginExtensions)
    }

    LOG.info("Loaded ${extensions.size} extensions")

    extensions.filterIsInstance<RouteExtension<*>>()
        .forEach { plugin ->
            val (name, description) = plugin.matcher
            RouteRuleRegister.addRule(name, description)
        }

    extensions.filterIsInstance<CmdExtension<*, *, *>>()
        .forEach { plugin ->
            val cmdName = plugin.cmd
            CmdRuleRegister.addRule(cmdName)
        }

    val database = DatabaseImpl()

    factory<Scheduler> {
        SchedulerImpl(it[0], get())
    }

    extensions.forEach { plugin ->
        val pluginDef = buildPluginDef(plugin)
        val mem = MemImpl()
        val kv = KvImpl(pluginDef)
        val blob = BlobImpl(pluginDef)
        val vector = VectorImpl(pluginDef)

        val config = ConfigImpl(plugin)

        val server = ServerImpl(pluginDef)

        var context: PluginContext? = null

        single(named(pluginDef.name)) {
            runCatching {
                plugin.apply {
                    context = PluginContextImpl(
                        pluginDef,
                        mem,
                        kv,
                        blob,
                        vector,
                        config,
                        database,
                        get { parametersOf(pluginDef.name) },
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
            }.onFailure {
                LOG.warn("Failed to load extension ${plugin.name}", it)
            }.getOrDefault(plugin)
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