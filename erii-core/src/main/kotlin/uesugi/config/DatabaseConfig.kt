package uesugi.config

import org.h2.tools.Server
import uesugi.LOG

fun configureH2Console() {
    val enabled = System.getProperty("h2.console.enabled", "false").toBoolean()
    val port = System.getProperty("h2.console.port", "8082")
    if (!enabled) return

    val externalNames = System.getProperty("h2.console.externalNames", "")
    val args = mutableListOf("-web", "-webAllowOthers", "-webPort", port)
    if (externalNames.isNotBlank()) {
        args.addAll(listOf("-webExternalNames", externalNames))
    }

    val h2Console = Server.createWebServer(*args.toTypedArray())
    h2Console.start()
    val displayHost = externalNames.split(",").firstOrNull()?.trim()?.takeIf { it.isNotBlank() } ?: "localhost"
    LOG.info("H2 console started at http://${displayHost}:${port}")
}
