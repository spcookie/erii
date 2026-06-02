package uesugi.plugin

import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import uesugi.common.toolkit.ConfigHolder
import uesugi.server.SystemConfigHolder
import uesugi.spi.PluginDef
import uesugi.spi.Server

class ServerImpl(val defined: PluginDef) : Server {

    override val url: URLBuilder
        get() = URLBuilder().apply {
            protocol = URLProtocol.HTTP
            host = ConfigHolder.getBrowserExternalUrl()
            port = _port
            pathSegments = listOf("plugin", defined.name)
        }

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

    override fun route(conf: Route.() -> Unit) {
        routeing.route("/${defined.name}") {
            conf()
        }
    }

}
