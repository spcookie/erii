package uesugi.plugin

import kotlinx.coroutines.runBlocking
import uesugi.spi.ExpireStrategy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class MemImplTest {
    @Test
    fun `get reads values stored in expiring buckets`() = runBlocking {
        val mem = MemImpl()
        try {
            mem.set("key", "expiring", 1.minutes, ExpireStrategy.AFTER_WRITE)

            assertEquals("expiring", mem.get("key"))
        } finally {
            mem.close()
        }
    }

    @Test
    fun `new writes replace older buckets for same key`() = runBlocking {
        val mem = MemImpl()
        try {
            mem.set("key", "first", 1.minutes, ExpireStrategy.AFTER_WRITE)
            mem.set("key", "second", 1.seconds, ExpireStrategy.AFTER_ACCESS)
            assertEquals("second", mem.get("key"))

            mem.set("key", "default")
            assertEquals("default", mem.get("key"))

            mem.delete("key")
            assertNull(mem.get("key"))
        } finally {
            mem.close()
        }
    }
}
