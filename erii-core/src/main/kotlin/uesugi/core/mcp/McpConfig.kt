package uesugi.core.mcp

enum class McpTransport {
    STDIO,
    SSE,
    STREAMABLE_HTTP,
    WEBSOCKET
}

data class McpServerConfig(
    val fileName: String,
    val enabled: Boolean,
    val transport: McpTransport,
    val name: String,
    val command: String? = null,
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap(),
    val cwd: String? = null,
    val url: String? = null,
    val headers: Map<String, String> = emptyMap()
)
