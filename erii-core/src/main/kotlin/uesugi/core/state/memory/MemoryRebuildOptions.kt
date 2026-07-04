package uesugi.core.state.memory

data class MemoryRebuildOptions(
    val vector: Boolean = false,
    val graph: Boolean = false
) {
    companion object {
        private const val VECTOR_PROPERTY = "memory.rebuild.vector"
        private const val GRAPH_PROPERTY = "memory.rebuild.graph"
        private const val VECTOR_ENV = "MEMORY_REBUILD_VECTOR"
        private const val GRAPH_ENV = "MEMORY_REBUILD_GRAPH"

        fun from(
            env: Map<String, String> = System.getenv(),
            property: (String) -> String? = System::getProperty
        ): MemoryRebuildOptions =
            MemoryRebuildOptions(
                vector = parseFlag(property(VECTOR_PROPERTY) ?: env[VECTOR_ENV]),
                graph = parseFlag(property(GRAPH_PROPERTY) ?: env[GRAPH_ENV])
            )

        private fun parseFlag(raw: String?): Boolean =
            raw?.trim()?.lowercase() in setOf("1", "true", "yes", "y", "on")
    }
}

data class MemoryRebuildResult(
    val facts: Int,
    val groups: List<String>
)
