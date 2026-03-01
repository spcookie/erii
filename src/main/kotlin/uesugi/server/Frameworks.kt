package uesugi.server

import io.ktor.server.application.*
import io.ktor.server.config.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.context.loadKoinModules
import org.koin.environmentProperties
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.ktor.plugin.koin
import org.koin.logger.slf4jLogger
import uesugi.config.*
import uesugi.core.plugin.pluginModule

fun Application.configureFrameworks() {
    install(Koin) {
        slf4jLogger()
        environmentProperties()
    }

    configBaseModule()

    loadKoinModules(listOf(infrastructureModule, serviceModule, adapterModule))
    loadKoinModules(pluginModule())

    koin().createEagerInstances()

    migrationIf(
        environment.config.propertyOrNull("migration")?.getAs() ?: false,
        inject<Database>().value
    )

    warmUp()
}
