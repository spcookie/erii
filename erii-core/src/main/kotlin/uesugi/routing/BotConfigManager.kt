package uesugi.routing

import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import org.koin.ktor.ext.inject
import uesugi.common.RefreshManager
import uesugi.plugin.PluginLifecycleManager
import uesugi.plugin.PluginRefreshResult

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
    }
}

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
