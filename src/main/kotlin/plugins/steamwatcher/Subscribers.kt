package plugins.steamwatcher

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

    init {
        loadSubscriptionsFromEnv()
    }

    private fun loadSubscriptionsFromEnv() {
        val subscriptionsEnv = System.getenv("STEAM_SUBSCRIPTIONS") ?: return

        subscriptionsEnv.split(";").forEach { entry ->
            val parts = entry.trim().split(",")
            if (parts.size == 3) {
                try {
                    val groupId = parts[0].trim().toLong()
                    val qqId = parts[1].trim().toLong()
                    val steamId = parts[2].trim()
                    bindings.add(Subscription(groupId, qqId, steamId))
                } catch (_: NumberFormatException) {
                    println("Invalid subscription entry: $entry")
                }
            }
        }
    }
}