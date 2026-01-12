package uesugi

import com.bcz.Subscribers
import io.ktor.server.application.*
import org.h2.tools.Server
import uesugi.server.*
import uesugi.toolkit.logger

const val DEBUG_GROUP_ID: String = "474270623"

val ENABLE_GROUPS = listOf(
    "1053148332", "474270623"
)

val MESSAGE_REDIRECT_GROUP_MAP = mapOf(
    "474270623" to "1053148332"
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

    configureConnectBots()
    configureBotAgent()
    configureH2Console()

    Subscribers.bindings += Subscribers.Subscription(
        1053148332,
        2697951448,
        "76561198415512702"
    )
}

fun configureH2Console() {
    val enabled = System.getProperty("h2.console.enabled", "true").toBoolean()
    if (!enabled) return
    val h2Console = Server.createWebServer("-web", "-webPort", "8082")
    h2Console.start()
    LOG.info("H2 console started at http://localhost:8082")
}