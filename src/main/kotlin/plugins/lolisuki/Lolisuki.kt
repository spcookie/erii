package uesugi.plugins.lolisuki

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import uesugi.toolkit.logger

class Lolisuki {
    private val client = HttpClient(CIO) {
        engine {
            val httpProxy = System.getProperty("http.proxy")
            if (httpProxy != null) {
                proxy = ProxyBuilder.http(httpProxy)
            }
        }
    }

    private val log = logger()
}