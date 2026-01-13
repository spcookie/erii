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
        bindings.add(
            Subscription(
                1053148332,
                2697951448,
                "76561198415512702"
            )
        )
        bindings.add(
            Subscription(
                1053148332,
                1,
                "76561199087375065"
            )
        )
        bindings.add(
            Subscription(
                1053148332,
                2,
                "76561198308338531"
            )
        )
        bindings.add(
            Subscription(
                1053148332,
                3,
                "76561199095098310"
            )
        )
        bindings.add(
            Subscription(
                1053148332,
                4,
                "76561199083740317"
            )
        )
    }
}