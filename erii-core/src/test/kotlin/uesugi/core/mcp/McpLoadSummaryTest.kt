package uesugi.core.mcp

import kotlin.test.Test
import kotlin.test.assertEquals

class McpLoadSummaryTest {
    @Test
    fun `formats load summary with counts and names`() {
        val summary = McpLoadSummary(
            configured = listOf("local", "remote", "broken", "local"),
            loaded = listOf("local", "remote"),
            failed = listOf("broken"),
            duplicates = listOf("local")
        )

        assertEquals(
            "MCP load summary: configured=4 loaded=2 failed=1 duplicates=1 " +
                    "loaded=[local, remote] failed=[broken] duplicates=[local]",
            formatMcpLoadSummary(summary)
        )
    }
}
