package uesugi.core.route

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class RouteCallEvent(
    val botId: String,
    val groupId: String,
    val senderId: String,
    val input: String,
    val hit: RouteRule,
    val echo: String = Uuid.random().toHexString(),
) {
    infix fun hit(rule: RouteRule): Boolean {
        return this.hit == rule
    }

    infix fun hit(name: String): Boolean {
        return this.hit.name.equals(name, ignoreCase = true)
    }
}