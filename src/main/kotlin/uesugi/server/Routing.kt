package uesugi.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import uesugi.routing.configureBotStatus
import uesugi.routing.configureMeme
import uesugi.toolkit.JSON

fun Application.configureRouting() {
    install(Resources)
    install(DoubleReceive)
    install(AutoHeadResponse)
    install(ContentNegotiation) {
        json(JSON)
    }
    routing {
        configureBotStatus()
        configureMeme()
        authenticate("basic") {
            staticResources("/", "public")
        }
    }
}
