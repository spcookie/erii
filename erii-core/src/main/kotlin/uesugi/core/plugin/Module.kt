package uesugi.core.plugin

import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.pf4j.*
import uesugi.LOG
import uesugi.config.HttpClientFactory
import uesugi.core.route.CmdRuleRegister
import uesugi.core.route.RouteRuleRegister
import uesugi.spi.*
import java.nio.file.Path


class AgentPluginFactory : DefaultPluginFactory() {
    override fun create(pluginWrapper: PluginWrapper): Plugin? {
        val plugin = super.create(pluginWrapper)
        if (plugin !is AgentPlugin) return null
        plugin.wrapper = pluginWrapper
        return plugin
    }
}

class AgentPluginLoader(pluginManager: PluginManager) : DefaultPluginLoader(pluginManager) {
    override fun createPluginClassLoader(
        pluginPath: Path?,
        pluginDescriptor: PluginDescriptor?
    ): PluginClassLoader? {
        return PluginClassLoader(pluginManager, pluginDescriptor, javaClass.getClassLoader())
        return super.createPluginClassLoader(pluginPath, pluginDescriptor)
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

    val extensions = pluginManager.startedPlugins.flatMap { pluginWrapper ->
        runCatching {
            pluginManager.getExtensions(AgentExtension::class.java, pluginWrapper.pluginId)
        }.onFailure {
            LOG.warn("Failed to get extensions for ${pluginWrapper.pluginId}", it)
        }.getOrDefault(emptyList())
    }

    LOG.info("Loaded ${extensions.size} extensions")

    extensions.filterIsInstance<RouteExtension>()
        .forEach { plugin ->
            val (name, description) = plugin.matcher
            RouteRuleRegister.addRule(name, description)
        }

    extensions.filterIsInstance<CmdExtension<*, *>>()
        .forEach { plugin ->
            val cmdName = plugin.cmd
            CmdRuleRegister.addRule(cmdName)
        }

    val database = DatabaseImpl()

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