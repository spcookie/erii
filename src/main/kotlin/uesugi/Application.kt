package uesugi

import io.ktor.server.application.*
import uesugi.config.configureH2Console
import uesugi.server.*

internal val LOG = _root_ide_package_.uesugi.toolkit.logger("uesugi")

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureFrameworks()
    configureMonitoring()
    configureSecurity()
    configureHTTP()
    configureRouting()

    configureBotAgent()
    configureConnectBots()
    configureH2Console()
}