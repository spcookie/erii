package uesugi.core

data class RouteCallEvent(
    val botId: String,
    val groupId: String,
    val input: String,
    val hit: RouteRule
) {
    infix fun hit(rule: RouteRule): Boolean {
        return this.hit == rule
    }
}