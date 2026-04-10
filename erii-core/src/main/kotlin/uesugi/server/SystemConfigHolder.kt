package uesugi.server

import io.ktor.server.application.*
import io.ktor.server.config.*

object SystemConfigHolder {
    lateinit var config: ApplicationConfig

    fun init(app: Application) {
        config = app.environment.config
    }

}