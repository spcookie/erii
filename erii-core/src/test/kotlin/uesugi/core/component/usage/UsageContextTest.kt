package uesugi.core.component.usage

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UsageContextTest {
    @Test
    fun `withUsage exposes identity only inside its scope`() = runBlocking {
        assertNull(UsageContext.current())

        UsageContext.withUsage("bot-a", "group-1") {
            assertEquals(UsageIdentity("bot-a", "group-1"), UsageContext.current())
        }

        assertNull(UsageContext.current())
    }

    @Test
    fun `withUsage propagates to child coroutines and restores nested scopes`() = runBlocking {
        UsageContext.withUsage("bot-a", "group-1") {
            coroutineScope {
                val child = async {
                    UsageContext.current()
                }
                assertEquals(UsageIdentity("bot-a", "group-1"), child.await())
            }

            UsageContext.withUsage("bot-b", "group-2") {
                assertEquals(UsageIdentity("bot-b", "group-2"), UsageContext.current())
            }

            assertEquals(UsageIdentity("bot-a", "group-1"), UsageContext.current())
        }

        assertNull(UsageContext.current())
    }
}
