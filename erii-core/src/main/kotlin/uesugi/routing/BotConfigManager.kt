package uesugi.routing

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import uesugi.common.RefreshManager

fun Routing.configureBotConfigManager() {
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
    }
}

private fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    is Map<*, *> -> buildJsonObject {
        this@toJsonElement.forEach { (k, v) -> put(k.toString(), (v as? Any?).toJsonElement()) }
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
