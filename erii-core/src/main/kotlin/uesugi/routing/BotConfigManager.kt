package uesugi.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import org.koin.ktor.ext.inject
import uesugi.common.EventBus
import uesugi.common.RefreshManager
import uesugi.common.event.CliPluginEvent
import uesugi.common.event.CliPluginReplyEvent
import uesugi.plugin.PluginCommandExample
import uesugi.plugin.PluginCommandExampleRegistry
import uesugi.plugin.PluginLifecycleManager
import uesugi.plugin.PluginRefreshResult
import java.util.*

fun Routing.configureBotConfigManager() {
    val pluginLifecycleManager by inject<PluginLifecycleManager>()

    authenticate("basic") {
        post("/api/config/refresh") {
            val results = RefreshManager.refreshAll()
            call.respond(
                buildJsonObject {
                    put("status", "ok")
                    put("message", "config refreshed")
                    results.forEach { (k, v) -> put(k, v.toJsonElement()) }
                }
            )
        }

        post("/api/plugins/refresh") {
            val result = pluginLifecycleManager.refreshAll()
            call.respond(result.httpStatus(), result.toJson())
        }

        post("/api/plugins/{id}/refresh") {
            val pluginId = call.parameters["id"].orEmpty()
            val result = pluginLifecycleManager.refreshPlugin(pluginId)
            call.respond(result.httpStatus(), result.toJson())
        }

        post("/api/plugins/cli/send") {
            val request = call.receive<CliPluginSendRequest>()
            val input = request.input.trim()
            if (input.isBlank()) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    buildJsonObject {
                        put("status", "error")
                        put("message", "input must not be blank")
                    },
                )
                return@post
            }

            val echo = UUID.randomUUID().toString()
            val replies = mutableListOf<String?>()
            val replyHandler: (CliPluginReplyEvent) -> Unit = EventBus.subscribeSync<CliPluginReplyEvent> { event ->
                if (event.echo == echo) {
                    replies += event.message
                }
            }
            try {
                EventBus.postSync(CliPluginEvent(input, echo))
            } finally {
                EventBus.unsubscribeSync(replyHandler)
            }

            val reply = replies.lastOrNull()
            call.respond(
                buildJsonObject {
                    put("status", "ok")
                    put("message", "plugin event sent")
                    put("input", input)
                    put("echo", echo)
                    put("reply", reply?.let { JsonPrimitive(it) } ?: JsonNull)
                },
            )
        }

        get("/api/plugins/cli/match") {
            val query = call.request.queryParameters["query"].orEmpty()
            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(0, 100) ?: 20
            val matches = PluginCommandExampleRegistry.match(query, limit)
            call.respond(
                buildJsonObject {
                    put("status", "ok")
                    put("query", query)
                    putJsonArray("matches") {
                        matches.forEach { add(it.toJson()) }
                    }
                },
            )
        }
    }
}

@Serializable
data class CliPluginSendRequest(
    val input: String,
)

private fun PluginRefreshResult.httpStatus(): HttpStatusCode = when (status) {
    "ok" -> HttpStatusCode.OK
    "not_found" -> HttpStatusCode.NotFound
    "unsupported" -> HttpStatusCode.BadRequest
    else -> HttpStatusCode.InternalServerError
}

private fun PluginRefreshResult.toJson(): JsonObject = buildJsonObject {
    put("status", status)
    put("message", message)
    requestedPluginId?.let { put("requestedPluginId", it) }
    putJsonArray("refreshedPlugins") {
        refreshedPlugins.forEach { add(it) }
    }
    put("loadedExtensions", loadedExtensions)
    putJsonObject("failedPlugins") {
        failedPlugins.forEach { (pluginId, reason) -> put(pluginId, reason) }
    }
}

private fun PluginCommandExample.toJson(): JsonObject = buildJsonObject {
    put("pluginId", pluginId)
    put("extensionName", extensionName)
    put("example", example)
    put("description", description)
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Map<*, *> -> buildJsonObject {
        this@toJsonElement.forEach { (k, v) -> put(k.toString(), v.toJsonElement()) }
    }

    is Iterable<*> -> buildJsonArray {
        this@toJsonElement.forEach { add(it.toJsonElement()) }
    }

    else -> {
        runCatching {
            @OptIn(InternalSerializationApi::class)
            @Suppress("UNCHECKED_CAST")
            Json.encodeToJsonElement(this::class.serializer() as KSerializer<Any>, this)
        }.getOrElse { JsonPrimitive(this.toString()) }
    }
}
