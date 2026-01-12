package uesugi.routing

import io.ktor.server.response.*
import io.ktor.server.routing.*
import uesugi.server.BotManage

fun Routing.configureBotStatus() {
    get("/bots") {
        call.respond(BotManage.getAllBotIds())
    }
    get("/status/{id}") {
        val id = call.request.pathVariables["id"]

    }
}