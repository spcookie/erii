package uesugi.server

import io.ktor.server.application.*
import org.koin.core.context.GlobalContext.loadKoinModules
import org.koin.environmentProperties
import org.koin.fileProperties
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import uesugi.config.*

fun Application.configureFrameworks() {
    install(Koin) {
        slf4jLogger()
        fileProperties("/application.yaml")
        properties(
            mapOf(
                "llm.proxy" to environment.config.property("llm.proxy").getString()
            )
        )

        environmentProperties()
        modules(configModule)
        createEagerInstances()
    }
    loadKoinModules(
        listOf(adapterModule, infrastructureModule, serviceModule, pluginModule),
        true
    )
}
