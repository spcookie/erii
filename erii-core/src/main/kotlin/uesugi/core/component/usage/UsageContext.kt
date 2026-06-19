package uesugi.core.component.usage

import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.withContext

data class UsageIdentity(
    val botId: String,
    val groupId: String
)

object UsageContext {
    private val currentIdentity = ThreadLocal<UsageIdentity?>()

    suspend fun <T> withUsage(
        botId: String,
        groupId: String,
        block: suspend () -> T
    ): T {
        val identity = UsageIdentity(botId, groupId)
        return withContext(currentIdentity.asContextElement(identity)) {
            block()
        }
    }

    fun current(): UsageIdentity? = currentIdentity.get()
}
