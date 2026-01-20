package uesugi.server

import io.ktor.server.application.*
import org.koin.core.context.loadKoinModules
import org.koin.environmentProperties
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import uesugi.config.*
import uesugi.plugins.pluginModule

fun Application.configureFrameworks() {
    configBaseModule()
    configModule()
    install(Koin) {
        slf4jLogger()
        modules(adapterModule, infrastructureModule, serviceModule)
        environmentProperties()
        createEagerInstances()
    }

    loadKoinModules(listOf(pluginModule()))
}
