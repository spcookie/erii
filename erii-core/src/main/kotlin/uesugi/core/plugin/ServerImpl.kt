package uesugi.core.plugin

import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import uesugi.spi.PluginDef
import uesugi.spi.Server

class ServerImpl(val defined: PluginDef) : Server {

    private val routeing by lazy {
        var ref: Route? = null
        embeddedServer(Netty, configure = {
            connectors.add(EngineConnectorBuilder().apply {
                host = "127.0.0.1"
                port = 8888
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

    override fun route(conf: Route.() -> Unit) {
        routeing.route("/${defined.name}") {
            conf()
        }
    }

}
