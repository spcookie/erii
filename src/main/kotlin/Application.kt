package uesugi

import io.ktor.server.application.*
import org.h2.tools.Server
import uesugi.server.configureFrameworks
import uesugi.server.configureHTTP
import uesugi.server.configureMonitoring
import uesugi.server.configureRouting
import uesugi.toolkit.logger

val DEBUG_GROUP_ID: String? = System.getenv("DEBUG_GROUP_ID")

val ENABLE_GROUPS: List<String> = System.getenv("ENABLE_GROUPS")
    ?.split(",")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?: emptyList()

val MESSAGE_REDIRECT_GROUP_MAP: Map<String, String> = System.getenv("MESSAGE_REDIRECT_MAP")
    ?.split(",")
    ?.mapNotNull { entry ->
        val parts = entry.trim().split(":")
        if (parts.size == 2) parts[0] to parts[1] else null
    }
    ?.toMap()
    ?: emptyMap()

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