package uesugi.config

import org.h2.tools.Server
import uesugi.LOG

fun configureH2Console() {
    val enabled = System.getProperty("h2.console.enabled", "false").toBoolean()
    val port = System.getProperty("h2.console.port", "8082")
    if (!enabled) return
    val h2Console = Server.createWebServer("-web", "-webPort", port)
    h2Console.start()
    LOG.info("H2 console started at http://localhost:${port}")
}