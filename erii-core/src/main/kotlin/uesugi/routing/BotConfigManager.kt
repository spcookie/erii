package uesugi.routing

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import uesugi.common.RefreshManager

fun Routing.configureBotConfigManager() {
    authenticate("basic") {
        post("/api/config/refresh") {
            val results = RefreshManager.refreshAll()
            call.respond(
                mapOf(
                    "status" to "ok",
                    "message" to "config refreshed",
                ) + results
            )
        }
    }
}
