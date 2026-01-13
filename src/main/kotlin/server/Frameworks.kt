package uesugi.server

import io.ktor.server.application.*
import org.koin.core.context.GlobalContext.loadKoinModules
import org.koin.environmentProperties
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import uesugi.config.adapterModule
import uesugi.config.configModule
import uesugi.config.infrastructureModule
import uesugi.config.serviceModule
import uesugi.plugins.pluginModule

fun Application.configureFrameworks() {
    install(Koin) {
        slf4jLogger()
        environmentProperties()
        modules(configModule)
        createEagerInstances()
    }
    loadKoinModules(
        listOf(adapterModule, infrastructureModule, serviceModule, pluginModule()),
        true
    )
}
