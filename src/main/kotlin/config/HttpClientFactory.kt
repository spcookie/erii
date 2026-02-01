package uesugi.config

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*

class HttpClientFactory {

    enum class Type {
        NO_PROXY, PROXY
    }

    fun createProxyClient() = HttpClient(CIO) {
        engine {
            val httpProxy = System.getenv("HTTP_PROXY")
            if (httpProxy != null) {
                proxy = ProxyBuilder.http(httpProxy)
            }
        }
        install(ContentNegotiation) {
            jackson()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000   // 整个请求（包含重试、重定向）
            connectTimeoutMillis = 20_000   // 建立 TCP 连接
            socketTimeoutMillis = 60_000   // 读写超时
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