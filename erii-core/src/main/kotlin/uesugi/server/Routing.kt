package uesugi.server

import gg.jte.TemplateEngine
import gg.jte.resolve.ResourceCodeResolver
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.jte.*
import io.ktor.server.plugins.autohead.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.doublereceive.*
import io.ktor.server.resources.*
import io.ktor.server.routing.*
import uesugi.common.JSON
import uesugi.routing.configureBotStatus
import gg.jte.ContentType as JteContentType

fun Application.configureRouting() {
    install(Resources)
    install(DoubleReceive)
    install(AutoHeadResponse)
    install(ContentNegotiation) {
        json(JSON)
    }
    install(Jte) {
        val codeResolver = ResourceCodeResolver("jte", Routing::class.java.classLoader)
        templateEngine = TemplateEngine.create(codeResolver, JteContentType.Html)
    }
    routing {
        configureBotStatus()
        authenticate("basic") {
            staticResources("/", "assets")
        }
    }
}
