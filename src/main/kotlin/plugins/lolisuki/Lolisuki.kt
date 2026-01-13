package uesugi.plugins.lolisuki

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import plugins.Plugin
import uesugi.core.RouteCallEvent
import uesugi.core.RouteRule
import uesugi.toolkit.EventBus
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

    internal val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())


    override fun onLoad() {
//        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()
        EventBus.subscribeAsync<RouteCallEvent>(scope) { event ->
            if (event.hit == RouteRule.REQUEST_R18_CONTENT) {
                client.get("https://lolisuki.cn/api/setu/v1") {
                    parameter("r18", 1)
                    parameter("level", 4)
                }
            }
        }
        TODO("Not yet implemented")
    }

    override fun onUnload() {
        TODO("Not yet implemented")
    }
}