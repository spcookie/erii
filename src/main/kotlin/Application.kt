package uesugi

import io.ktor.server.application.*
import uesugi.server.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureFrameworks()
    configureMonitoring()
    configureHTTP()
    configureRouting()

    configureConnectBots()
    configureBotAgent()
    configureH2Console()
}
