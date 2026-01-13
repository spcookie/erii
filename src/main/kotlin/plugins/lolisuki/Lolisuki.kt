package uesugi.plugins.lolisuki

import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.context.GlobalContext
import plugins.Plugin
import uesugi.core.RouteCallEvent
import uesugi.core.RouteRule
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger

class Lolisuki : Plugin {

    val client by GlobalContext.get().inject<HttpClient>()

    private val log = logger()

    internal val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onLoad() {
//        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()
        EventBus.subscribeAsync<RouteCallEvent>(scope) { event ->
            if (event.hit == RouteRule.REQUEST_R18_CONTENT) {
                val node: JsonNode = client.get("https://lolisuki.cn/api/setu/v1") {
                    parameter("r18", 1)
                    parameter("level", 4)
                }.body()
                if (node.get("code").asInt() != 0) {
                    log.error("Lolisuki error: ${node.get("message").asText()}")
                } else {
                    val data = node.get("data")
                }
            }
        }
        TODO("Not yet implemented")
    }

    override fun onUnload() {
        TODO("Not yet implemented")
    }
}