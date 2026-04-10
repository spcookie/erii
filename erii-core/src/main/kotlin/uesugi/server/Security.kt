package uesugi.server

import io.ktor.server.application.*
import io.ktor.server.auth.*

fun Application.configureSecurity() {
    val username = environment.config.property("security.username").getString()
    val password = environment.config.property("security.password").getString()
    authentication {
        basic(name = "basic") {
            realm = "uesugi"
            validate { credentials ->
                if (credentials.name == username && credentials.password == password) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }

    }
}
