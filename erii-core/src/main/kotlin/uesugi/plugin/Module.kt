package uesugi.plugin

import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.pf4j.*
import uesugi.config.HttpClientFactory
import uesugi.spi.AgentPlugin
import uesugi.spi.Scheduler


class AgentPluginFactory : DefaultPluginFactory() {
    override fun create(pluginWrapper: PluginWrapper): Plugin? {
        val plugin = super.create(pluginWrapper)
        if (plugin !is AgentPlugin) return null
        plugin.wrapper = pluginWrapper
        return plugin
    }
}

class AgentPluginManager : DefaultPluginManager {
    constructor() : super()

    override fun createPluginFactory(): PluginFactory {
        return AgentPluginFactory()
    }

    override fun createExtensionFactory(): ExtensionFactory {
        return SingletonExtensionFactory(this)
    }

    override fun createPluginLoader(): PluginLoader? {
        return super.createPluginLoader()
    }

    fun stopPluginOnly(pluginId: String): PluginState {
        return stopPlugin(pluginId, false)
    }

    fun unloadPluginOnly(pluginId: String): Boolean {
        return unloadPlugin(pluginId, false)
    }
}

fun pluginModule() = module(createdAtStart = true) {
    val pluginManager = AgentPluginManager()

    single { pluginManager }

    single {
        PluginLifecycleManager(
            pluginManager,
            get(),
            get(named(HttpClientFactory.Type.NO_PROXY)),
            get(named(HttpClientFactory.Type.PROXY)),
        )
    } onClose {
        it?.shutdown()
    }

    factory<Scheduler> {
        SchedulerImpl(it[0], get())
    }
}
