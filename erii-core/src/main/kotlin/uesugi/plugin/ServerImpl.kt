package uesugi.plugin

import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import uesugi.server.SystemConfigHolder
import uesugi.spi.PluginDef
import uesugi.spi.Server

class ServerImpl(val defined: PluginDef) : Server {

    override val port: Int
        get() = _port

    companion object {
        private val _port by lazy {
            SystemConfigHolder.config.property("ktor.deployment.port").getString().toInt() + 100
        }

        private val routeing by lazy {
            var ref: Route? = null
            embeddedServer(Netty, configure = {
                connectors.add(EngineConnectorBuilder().apply {
                    host = "0.0.0.0"
                    port = _port
                })
                connectionGroupSize = 2
                workerGroupSize = 5
                callGroupSize = 10
            }) {
                install(ContentNegotiation) {
                    jackson()
                }

                routing {
                    route("/plugin") {
                        ref = this
                    }
                }
            }.start()
            ref!!
        }
    }

    override val basePath: String
        get() = "/plugin/${defined.name}"

    override fun route(conf: Route.() -> Unit) {
        routeing.route("/${defined.name}") {
            conf()
        }
    }

}
