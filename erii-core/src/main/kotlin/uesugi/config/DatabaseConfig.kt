package uesugi.config

import org.h2.tools.Server
import uesugi.LOG

fun configureH2Console() {
    val enabled = System.getProperty("h2.console.enabled", "true").toBoolean()
    if (!enabled) return
    val h2Console = Server.createWebServer("-web", "-webPort", "8082")
    h2Console.start()
    LOG.info("H2 console started at http://localhost:8082")
}