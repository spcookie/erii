package uesugi.plugin

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PluginCommandExampleRegistryTest {
    @AfterTest
    fun cleanUp() {
        PluginCommandExampleRegistry.clear()
    }

    @Test
    fun `matches examples case-insensitively across fields`() {
        PluginCommandExampleRegistry.register("music", "Search", "play jay chou", "Search songs")
        PluginCommandExampleRegistry.register("image", "Draw", "draw cat", "Create image")

        assertEquals(listOf("play jay chou"), PluginCommandExampleRegistry.match("JAY").map { it.example })
        assertEquals(listOf("draw cat"), PluginCommandExampleRegistry.match("create").map { it.example })
        assertEquals(listOf("play jay chou"), PluginCommandExampleRegistry.match("music").map { it.example })
    }

    @Test
    fun `empty query returns registered examples up to limit`() {
        PluginCommandExampleRegistry.register("a", "A", "one")
        PluginCommandExampleRegistry.register("b", "B", "two")

        assertEquals(listOf("one"), PluginCommandExampleRegistry.match("", limit = 1).map { it.example })
    }

    @Test
    fun `blank examples are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            PluginCommandExampleRegistry.register("demo", "Demo", " ")
        }
    }

    @Test
    fun `removes examples by extension and plugin`() {
        PluginCommandExampleRegistry.register("demo", "One", "first")
        PluginCommandExampleRegistry.register("demo", "Two", "second")
        PluginCommandExampleRegistry.register("other", "One", "third")

        PluginCommandExampleRegistry.removeExtension("demo", "One")
        assertEquals(listOf("second", "third"), PluginCommandExampleRegistry.match("").map { it.example })

        PluginCommandExampleRegistry.removePlugin("demo")
        assertEquals(listOf("third"), PluginCommandExampleRegistry.match("").map { it.example })
    }
}
