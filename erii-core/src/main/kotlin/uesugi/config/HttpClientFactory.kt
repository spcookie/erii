package uesugi.config

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import uesugi.common.toolkit.ConfigHolder

class HttpClientFactory {

    enum class Type {
        NO_PROXY, PROXY
    }

    fun createProxyClient() = HttpClient(CIO) {
        engine {
            val httpProxy = ConfigHolder.getProxyHttp()
            if (httpProxy != null) {
                proxy = ProxyBuilder.http(httpProxy)
            }
        }
        install(ContentNegotiation) {
            jackson()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 60_000
        }
    }

    fun createClient() = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 60_000
        }
    }

}