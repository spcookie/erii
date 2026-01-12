package uesugi

import io.ktor.server.application.*
import uesugi.server.*

const val DEBUG_GROUP_ID: String = "474270623"

val ENABLE_GROUPS = listOf(
    "1053148332", "474270623"
)

val MESSAGE_REDIRECT_GROUP_MAP = mapOf(
    "474270623" to "1053148332"
)

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
