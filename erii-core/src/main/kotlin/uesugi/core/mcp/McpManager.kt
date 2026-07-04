package uesugi.core.mcp

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.mcp.McpToolRegistryProvider
import ai.koog.agents.mcp.defaultStdioTransport
import ai.koog.agents.mcp.metadata.McpServerInfo
import io.ktor.client.HttpClient
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.headers
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.SseClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.WebSocketClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.Transport
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uesugi.common.toolkit.logger
import java.io.File
import java.util.concurrent.TimeUnit

object McpManager {
    private val log = logger()
    private val lock = Mutex()
    private val loader = McpConfigLoader()
    private var loaded = false
    private var runtimes: Map<String, McpServerRuntime> = emptyMap()

    suspend fun load() {
        lock.withLock {
            ensureLoaded()
        }
    }

    suspend fun registry(): ToolRegistry = lock.withLock {
        ensureLoaded()
        runtimes.values.fold(ToolRegistry.EMPTY) { acc, runtime -> acc + runtime.registry }
    }

    suspend fun refresh() {
        lock.withLock {
            closeRuntimes()
            loaded = false
            ensureLoaded()
        }
    }

    suspend fun close() {
        lock.withLock {
            closeRuntimes()
            loaded = false
        }
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        val configs = loader.load()
        val next = linkedMapOf<String, McpServerRuntime>()
        val loadedNames = mutableListOf<String>()
        val failedNames = mutableListOf<String>()
        val duplicateNames = mutableListOf<String>()
        log.info(
            "Loading MCP servers: configured={} names=[{}]",
            configs.size,
            configs.joinToString(", ") { it.name }
        )
        for (config in configs) {
            if (next.containsKey(config.name)) {
                log.warn("Skipping duplicate MCP server name {}", config.name)
                duplicateNames += config.name
                continue
            }
            val runtime = runCatching { connect(config) }
                .onFailure { log.error("Failed to connect MCP server ${config.name}", it) }
                .getOrNull()
            if (runtime != null) {
                next[config.name] = runtime
                loadedNames += config.name
                log.info("Loaded MCP server {} with {} tools", config.name, runtime.registry.tools.size)
            } else {
                failedNames += config.name
            }
        }
        log.info(
            formatMcpLoadSummary(
                McpLoadSummary(
                    configured = configs.map { it.name },
                    loaded = loadedNames,
                    failed = failedNames,
                    duplicates = duplicateNames
                )
            )
        )
        runtimes = next
        loaded = true
    }

    private suspend fun connect(config: McpServerConfig): McpServerRuntime =
        when (config.transport) {
            McpTransport.STDIO -> connectStdio(config)
            McpTransport.SSE -> connectSse(config)
            McpTransport.STREAMABLE_HTTP -> connectStreamableHttp(config)
            McpTransport.WEBSOCKET -> connectWebSocket(config)
        }

    private suspend fun connectStdio(config: McpServerConfig): McpServerRuntime {
        val command = requireNotNull(config.command) { "command is required for stdio MCP ${config.name}" }
        val process = withContext(Dispatchers.IO) {
            ProcessBuilder(listOf(command) + config.args)
                .apply {
                    config.cwd?.takeIf { it.isNotBlank() }?.let { directory(File(it)) }
                    environment().putAll(config.env)
                }
                .start()
        }
        val client = Client(Implementation(MCP_CLIENT_NAME, MCP_CLIENT_VERSION))
        try {
            client.connect(McpToolRegistryProvider.defaultStdioTransport(process))
            val registry = McpToolRegistryProvider.fromClient(
                mcpClient = client,
                serverInfo = McpServerInfo(command = (listOf(command) + config.args).joinToString(" "))
            )
            return McpServerRuntime(
                name = config.name,
                registry = registry,
                client = client,
                process = process
            )
        } catch (e: Throwable) {
            runCatching { client.close() }
            process.destroyForcibly()
            throw e
        }
    }

    private suspend fun connectSse(config: McpServerConfig): McpServerRuntime {
        val url = requireNotNull(config.url) { "url is required for sse MCP ${config.name}" }
        val httpClient = HttpClient {
            install(SSE)
        }
        val transport = SseClientTransport(
            client = httpClient,
            urlString = url,
            requestBuilder = {
                headers {
                    config.headers.forEach { (key, value) -> append(key, value) }
                }
            }
        )
        return connectRemoteClient(config.name, url, httpClient, transport)
    }

    private suspend fun connectStreamableHttp(config: McpServerConfig): McpServerRuntime {
        val url = requireNotNull(config.url) { "url is required for streamable HTTP MCP ${config.name}" }
        val httpClient = HttpClient {
            install(SSE)
        }
        val transport = StreamableHttpClientTransport(
            client = httpClient,
            url = url,
            requestBuilder = {
                headers {
                    config.headers.forEach { (key, value) -> append(key, value) }
                }
            }
        )
        return connectRemoteClient(config.name, url, httpClient, transport)
    }

    private suspend fun connectWebSocket(config: McpServerConfig): McpServerRuntime {
        val url = requireNotNull(config.url) { "url is required for websocket MCP ${config.name}" }
        val httpClient = HttpClient {
            install(WebSockets)
        }
        val transport = WebSocketClientTransport(
            client = httpClient,
            urlString = url,
            requestBuilder = {
                headers {
                    config.headers.forEach { (key, value) -> append(key, value) }
                }
            }
        )
        return connectRemoteClient(config.name, url, httpClient, transport)
    }

    private suspend fun connectRemoteClient(
        name: String,
        url: String,
        httpClient: HttpClient,
        transport: Transport
    ): McpServerRuntime {
        val client = Client(Implementation(MCP_CLIENT_NAME, MCP_CLIENT_VERSION))
        try {
            client.connect(transport)
            val registry = McpToolRegistryProvider.fromClient(
                mcpClient = client,
                serverInfo = McpServerInfo(url = url)
            )
            return McpServerRuntime(
                name = name,
                registry = registry,
                client = client,
                httpClient = httpClient
            )
        } catch (e: Throwable) {
            runCatching { client.close() }
            httpClient.close()
            throw e
        }
    }

    private suspend fun closeRuntimes() {
        runtimes.values.forEach { runtime ->
            runCatching { runtime.client.close() }
                .onFailure { log.warn("Failed to close MCP client {}", runtime.name, it) }
            runtime.httpClient?.close()
            runtime.process?.let { process ->
                withContext(Dispatchers.IO) {
                    process.destroy()
                    if (!process.waitFor(1, TimeUnit.SECONDS)) {
                        process.destroyForcibly()
                    }
                }
            }
        }
        runtimes = emptyMap()
    }

    private data class McpServerRuntime(
        val name: String,
        val registry: ToolRegistry,
        val client: Client,
        val process: Process? = null,
        val httpClient: HttpClient? = null
    )

    private const val MCP_CLIENT_NAME = "erii"
    private const val MCP_CLIENT_VERSION = "1.0.0"
}

internal data class McpLoadSummary(
    val configured: List<String>,
    val loaded: List<String>,
    val failed: List<String>,
    val duplicates: List<String>
)

internal fun formatMcpLoadSummary(summary: McpLoadSummary): String =
    "MCP load summary: configured=${summary.configured.size} " +
        "loaded=${summary.loaded.size} failed=${summary.failed.size} duplicates=${summary.duplicates.size} " +
        "loaded=${summary.loaded.formatNames()} failed=${summary.failed.formatNames()} " +
        "duplicates=${summary.duplicates.formatNames()}"

private fun List<String>.formatNames(): String =
    joinToString(prefix = "[", postfix = "]")
