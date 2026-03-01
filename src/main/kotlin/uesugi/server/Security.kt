package uesugi.server

import io.ktor.server.application.*
import io.ktor.server.auth.*

fun Application.configureSecurity() {
    authentication {
        basic(name = "basic") {
            realm = "EriiX"
            validate { credentials ->
                if (credentials.name == "eriix" && credentials.password == "!@Aa123") {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }

    }
}
