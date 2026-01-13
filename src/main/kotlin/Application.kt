package uesugi

import io.ktor.server.application.*
import org.h2.tools.Server
import uesugi.server.configureFrameworks
import uesugi.server.configureHTTP
import uesugi.server.configureMonitoring
import uesugi.server.configureRouting
import uesugi.toolkit.logger

//val DEBUG_GROUP_ID: String? = "474270623"
val DEBUG_GROUP_ID: String? = null

val ENABLE_GROUPS = listOf(
    "1053148332", "474270623"
)

val MESSAGE_REDIRECT_GROUP_MAP = mapOf(
    "1053148332——" to "474270623"
)

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

fun configureH2Console() {
    val enabled = System.getProperty("h2.console.enabled", "true").toBoolean()
    if (!enabled) return
    val h2Console = Server.createWebServer("-web", "-webPort", "8082")
    h2Console.start()
    LOG.info("H2 console started at http://localhost:8082")
}