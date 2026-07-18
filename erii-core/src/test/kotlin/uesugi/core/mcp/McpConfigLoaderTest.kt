package uesugi.core.mcp

import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals

class McpConfigLoaderTest {
    @Test
    fun `loads enabled stdio and sse configs from directory`() {
        val dir = Files.createTempDirectory("erii-mcp-test")
        dir.resolve("local.json").writeText(
            """
            {
              "enabled": true,
              "transport": "stdio",
              "name": "local-tools",
              "command": "npx",
              "args": ["-y", "@example/server"],
              "env": {
                "TOKEN": "${'$'}{MCP_TOKEN}"
              },
              "cwd": "."
            }
            """.trimIndent()
        )
        dir.resolve("remote.json").writeText(
            """
            {
              "enabled": true,
              "transport": "sse",
              "name": "remote-tools",
              "url": "https://example.test/sse",
              "headers": {
                "Authorization": "Bearer ${'$'}{MCP_TOKEN}"
              }
            }
            """.trimIndent()
        )

        val configs = McpConfigLoader(
            configDir = dir.toString(),
            environment = mapOf("MCP_TOKEN" to "secret-token")
        ).load()

        assertEquals(listOf("local-tools", "remote-tools"), configs.map { it.name })
        assertEquals(McpTransport.STDIO, configs[0].transport)
        assertEquals("npx", configs[0].command)
        assertEquals(listOf("-y", "@example/server"), configs[0].args)
        assertEquals(mapOf("TOKEN" to "secret-token"), configs[0].env)
        assertEquals(".", configs[0].cwd)
        assertEquals(McpTransport.SSE, configs[1].transport)
        assertEquals("https://example.test/sse", configs[1].url)
        assertEquals(mapOf("Authorization" to "Bearer secret-token"), configs[1].headers)
    }

    @Test
    fun `skips disabled invalid and unsupported configs`() {
        val dir = Files.createTempDirectory("erii-mcp-invalid-test")
        dir.resolve("disabled.json").writeText(
            """
            {
              "enabled": false,
              "transport": "stdio",
              "name": "disabled",
              "command": "npx"
            }
            """.trimIndent()
        )
        dir.resolve("unsupported.json").writeText(
            """
            {
              "enabled": true,
              "transport": "http",
              "name": "unsupported",
              "url": "https://example.test/mcp"
            }
            """.trimIndent()
        )
        dir.resolve("missing-command.json").writeText(
            """
            {
              "enabled": true,
              "transport": "stdio",
              "name": "broken"
            }
            """.trimIndent()
        )
        dir.resolve("valid.json").writeText(
            """
            {
              "enabled": true,
              "transport": "sse",
              "name": "valid",
              "url": "https://example.test/sse"
            }
            """.trimIndent()
        )

        val configs = McpConfigLoader(configDir = dir.toString()).load()

        assertEquals(listOf("valid"), configs.map { it.name })
    }

    @Test
    fun `loads streamable http and websocket configs`() {
        val dir = Files.createTempDirectory("erii-mcp-network-test")
        dir.resolve("http.json").writeText(
            """
            {
              "enabled": true,
              "transport": "streamable_http",
              "name": "http-tools",
              "url": "https://example.test/mcp",
              "headers": {
                "Authorization": "Bearer ${'$'}{MCP_TOKEN}"
              }
            }
            """.trimIndent()
        )
        dir.resolve("ws.json").writeText(
            """
            {
              "enabled": true,
              "transport": "websocket",
              "name": "ws-tools",
              "url": "wss://example.test/mcp",
              "headers": {
                "X-Token": "${'$'}{MCP_TOKEN}"
              }
            }
            """.trimIndent()
        )

        val configs = McpConfigLoader(
            configDir = dir.toString(),
            environment = mapOf("MCP_TOKEN" to "secret-token")
        ).load()

        assertEquals(listOf("http-tools", "ws-tools"), configs.map { it.name })
        assertEquals(McpTransport.STREAMABLE_HTTP, configs[0].transport)
        assertEquals("https://example.test/mcp", configs[0].url)
        assertEquals(mapOf("Authorization" to "Bearer secret-token"), configs[0].headers)
        assertEquals(McpTransport.WEBSOCKET, configs[1].transport)
        assertEquals("wss://example.test/mcp", configs[1].url)
        assertEquals(mapOf("X-Token" to "secret-token"), configs[1].headers)
    }
}
