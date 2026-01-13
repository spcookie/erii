package uesugi.config

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*

class HttpClientFactory {

    fun createClient() = HttpClient(CIO) {
        engine {
            val httpProxy = System.getProperty("http.proxy")
            if (httpProxy != null) {
                proxy = ProxyBuilder.http(httpProxy)
            }
        }
        install(ContentNegotiation) {
            jackson()
        }
    }

}