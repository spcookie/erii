package uesugi.core.mcp

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uesugi.common.toolkit.logger
import java.io.File

class McpConfigLoader(
    private val configDir: String? = null,
    private val environment: Map<String, String> = System.getenv()
) {
    private val log = logger()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun load(): List<McpServerConfig> {
        val dir = resolveConfigDir()
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }
        return dir.listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.mapNotNull(::parseFile)
            ?: emptyList()
    }

    private fun resolveConfigDir(): File {
        val raw = configDir
            ?: System.getProperty("config.mcp.dir")
            ?: System.getenv("CONFIG_MCP_DIR")
            ?: DEFAULT_MCP_DIR
        return File(raw).toPath().toAbsolutePath().toFile()
    }

    private fun parseFile(file: File): McpServerConfig? {
        val root = runCatching {
            json.parseToJsonElement(file.readText()).jsonObject
        }.onFailure {
            log.warn("Skipping MCP config {}, invalid JSON: {}", file.name, it.message)
        }.getOrNull() ?: return null

        val enabled = root.boolean("enabled") ?: true
        if (!enabled) return null

        val name = root.string("name")?.takeIf { it.isNotBlank() }
        if (name == null) {
            log.warn("Skipping MCP config {}, missing name", file.name)
            return null
        }

        return when (root.string("transport")?.lowercase()) {
            "stdio" -> parseStdio(file, root, enabled, name)
            "sse" -> parseRemote(file, root, enabled, name, McpTransport.SSE)
            "streamablehttp", "streamable-http", "streamable_http" ->
                parseRemote(file, root, enabled, name, McpTransport.STREAMABLE_HTTP)
            "websocket", "web-socket", "web_socket", "ws" ->
                parseRemote(file, root, enabled, name, McpTransport.WEBSOCKET)
            null -> {
                log.warn("Skipping MCP config {}, missing transport", file.name)
                null
            }
            else -> {
                log.warn("Skipping MCP config {}, unsupported transport {}", file.name, root.string("transport"))
                null
            }
        }
    }

    private fun parseStdio(file: File, root: JsonObject, enabled: Boolean, name: String): McpServerConfig? {
        val command = root.string("command")?.takeIf { it.isNotBlank() }
        if (command == null) {
            log.warn("Skipping MCP stdio config {}, missing command", file.name)
            return null
        }
        return McpServerConfig(
            fileName = file.name,
            enabled = enabled,
            transport = McpTransport.STDIO,
            name = name,
            command = expand(command),
            args = root.stringList("args").map(::expand),
            env = root.stringMap("env").mapValues { expand(it.value) },
            cwd = root.string("cwd")?.takeIf { it.isNotBlank() }?.let(::expand)
        )
    }

    private fun parseRemote(
        file: File,
        root: JsonObject,
        enabled: Boolean,
        name: String,
        transport: McpTransport
    ): McpServerConfig? {
        val url = root.string("url")?.takeIf { it.isNotBlank() }
        if (url == null) {
            log.warn("Skipping MCP {} config {}, missing url", transport.name.lowercase(), file.name)
            return null
        }
        return McpServerConfig(
            fileName = file.name,
            enabled = enabled,
            transport = transport,
            name = name,
            url = expand(url),
            headers = root.stringMap("headers").mapValues { expand(it.value) }
        )
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.boolean(name: String): Boolean? =
        (this[name] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.stringList(name: String): List<String> =
        runCatching {
            this[name]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }
        }.getOrNull() ?: emptyList()

    private fun JsonObject.stringMap(name: String): Map<String, String> {
        val raw: JsonElement = this[name] ?: return emptyMap()
        return runCatching {
            raw.jsonObject.mapNotNull { (key, value) ->
                val stringValue = value.jsonPrimitive.contentOrNull ?: return@mapNotNull null
                key to stringValue
            }.toMap()
        }.getOrNull() ?: emptyMap()
    }

    private fun expand(value: String): String =
        ENV_REF.replace(value) { match ->
            val key = match.groupValues[1]
            environment[key] ?: match.value.also {
                log.warn("MCP config references missing environment variable {}", key)
            }
        }

    private companion object {
        const val DEFAULT_MCP_DIR = "mcp"
        val ENV_REF = Regex("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}")
    }
}
