package uesugi.server

import gg.jte.TemplateEngine
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
import uesugi.common.toolkit.JSON
import uesugi.routing.configureBotStatus
import uesugi.routing.configureBotStatusManager
import java.nio.file.Path
import gg.jte.ContentType as JteContentType

fun Application.configureRouting() {
    install(Resources)
    install(DoubleReceive)
    install(AutoHeadResponse)
    install(ContentNegotiation) {
        json(JSON)
    }
    install(Jte) {
        val isDevelopment = System.getProperty("intellij.debug.agent") != null
        templateEngine = if (isDevelopment) {
            val targetDirectory: Path = Path.of("erii-core/jte-classes")
            TemplateEngine.createPrecompiled(targetDirectory, JteContentType.Html)
        } else {
            TemplateEngine.createPrecompiled(JteContentType.Html)
        }
    }
    routing {
        configureBotStatus()
        configureBotStatusManager()
        authenticate("basic") {
            staticResources("/", "assets")
        }
    }
}
