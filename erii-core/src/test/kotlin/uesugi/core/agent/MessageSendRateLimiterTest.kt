package uesugi.core.agent

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageSendRateLimiterTest {
    @Test
    fun `consecutive sends wait for the configured interval`() = runBlocking {
        var currentTimeMs = 0L
        val delays = mutableListOf<Long>()
        val limiter = MessageSendRateLimiter(
            intervalMs = 1240L,
            nowMillis = { currentTimeMs },
            delayMillis = { delayMs ->
                delays += delayMs
                currentTimeMs += delayMs
            }
        )

        limiter.awaitTurn()
        limiter.awaitTurn()

        assertEquals(listOf(1240L), delays)
    }
}
