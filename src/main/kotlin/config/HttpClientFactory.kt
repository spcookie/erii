package uesugi.config

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
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
        install(HttpTimeout) {
            requestTimeoutMillis = 90_000   // 整个请求（包含重试、重定向）
            connectTimeoutMillis = 20_000   // 建立 TCP 连接
            socketTimeoutMillis = 60_000   // 读写超时
        }
    }

}