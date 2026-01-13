package uesugi.plugins.lolisuki

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import plugins.Plugin
import uesugi.toolkit.logger

class Lolisuki : Plugin {
    private val client = HttpClient(CIO) {
        engine {
            val httpProxy = System.getProperty("http.proxy")
            if (httpProxy != null) {
                proxy = ProxyBuilder.http(httpProxy)
            }
        }
    }

    private val log = logger()

    override fun onLoad() {
        TODO("Not yet implemented")
    }

    override fun onUnload() {
        TODO("Not yet implemented")
    }
}