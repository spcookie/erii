package com.bcz

import kotlinx.serialization.Serializable

object Subscribers {
    @Serializable
    data class Subscription(
        val groupId: Long,
        val qqId: Long,
        val steamId: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Subscription) return false
            return groupId == other.groupId && qqId == other.qqId && steamId == other.steamId
        }

        override fun hashCode(): Int {
            var result = groupId.hashCode()
            result = 31 * result + qqId.hashCode()
            result = 31 * result + steamId.hashCode()
            return result
        }
    }

    val bindings: MutableSet<Subscription> = mutableSetOf()
}