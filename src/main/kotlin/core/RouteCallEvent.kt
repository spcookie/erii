package uesugi.core

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class RouteCallEvent(
    val botId: String,
    val groupId: String,
    val input: String,
    val hit: RouteRule,
    val echo: String = Uuid.random().toHexString(),
) {
    infix fun hit(rule: RouteRule): Boolean {
        return this.hit == rule
    }
}