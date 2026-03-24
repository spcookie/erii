package uesugi.server

import io.ktor.server.application.*
import io.ktor.server.config.*
import kotlinx.coroutines.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.koin.core.context.loadKoinModules
import org.koin.environmentProperties
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.ktor.plugin.koin
import org.koin.logger.slf4jLogger
import uesugi.LOG
import uesugi.config.appModule
import uesugi.config.configBaseModule
import uesugi.config.migrationIf
import uesugi.config.warmUp
import uesugi.core.plugin.pluginModule

private val scope =
    CoroutineScope(Dispatchers.IO) + CoroutineName("Frameworks") + CoroutineExceptionHandler { _, exception ->
        LOG.error("Frameworks exception: {}", exception.message, exception)
    }

fun Application.configureFrameworks() {
    install(Koin) {
        slf4jLogger()
        environmentProperties()
    }

    configBaseModule()

    loadKoinModules(listOf(appModule))

    koin().createEagerInstances()

    migrationIf(
        environment.config.propertyOrNull("migration")?.getAs() ?: false,
        inject<Database>().value
    )

    warmUp()

    scope.launch {
        loadKoinModules(pluginModule())
        koin().createEagerInstances()
    }
}
