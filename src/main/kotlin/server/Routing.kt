package uesugi.server

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
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
//    routing {
//        get("/") {
//            call.respondText("Hello World!")
//        }
//        get<Articles> { article ->
//            // Get all articles ...
//            call.respond("List of articles sorted starting from ${article.sort}")
//        }
//        post("/double-receive") {
//            val first = call.receiveText()
//            val theSame = call.receiveText()
//            call.respondText("$first $theSame")
//        }
//    }
    install(ContentNegotiation) {
        json(JSON)
    }
    routing {
        configureBotStatus()
    }
}

//@Serializable
//@Resource("/articles")
//class Articles(val sort: String? = "new")
