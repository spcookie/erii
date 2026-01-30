package uesugi.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import uesugi.routing.configureBotStatus
import uesugi.toolkit.JSON

fun Application.configureRouting() {
    install(Resources)
//    install(RequestValidation) {
//        validate<String> { bodyText ->
//            if (!bodyText.startsWith("Hello"))
//                ValidationResult.Invalid("Body text should start with 'Hello'")
//            else ValidationResult.Valid
//        }
//    }
    install(DoubleReceive)
    install(AutoHeadResponse)
    install(ContentNegotiation) {
        json(JSON)
    }
    routing {
        // Configure bot status routes (dynamic content) - MUST be before catch-all static resources
        configureBotStatus()

        // Serve static files from public directory
        staticResources("/", "public")
    }
}
