package uesugi.routing

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import uesugi.common.toolkit.ConfigHolder

fun Routing.configureBotConfigManager() {
    authenticate("basic") {
        post("/api/config/refresh") {
            ConfigHolder.refresh()
            call.respond(mapOf("success" to true, "message" to "config cache refreshed"))
        }
    }
}
