package uesugi

import io.ktor.server.application.*
import uesugi.config.configureH2Console
import uesugi.server.configureFrameworks
import uesugi.server.configureHTTP
import uesugi.server.configureMonitoring
import uesugi.server.configureRouting
import uesugi.toolkit.logger

internal val LOG = logger("uesugi")

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureFrameworks()
    configureMonitoring()
    configureHTTP()
    configureRouting()

    configureBotAgent()
    configureConnectBots()
    configureH2Console()
}