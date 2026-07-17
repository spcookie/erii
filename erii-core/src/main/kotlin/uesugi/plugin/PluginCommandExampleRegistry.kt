package uesugi.plugin

data class PluginCommandExample(
    val pluginId: String,
    val extensionName: String,
    val example: String,
    val description: String,
)

object PluginCommandExampleRegistry {
    private val lock = Any()
    private val examples = linkedMapOf<String, MutableList<PluginCommandExample>>()

    fun register(pluginId: String, extensionName: String, example: String, description: String = "") {
        val normalizedExample = example.trim()
        require(normalizedExample.isNotBlank()) { "command example must not be blank" }
        val normalizedDescription = description.trim()
        val item = PluginCommandExample(
            pluginId = pluginId,
            extensionName = extensionName,
            example = normalizedExample,
            description = normalizedDescription,
        )
        synchronized(lock) {
            val key = key(pluginId, extensionName)
            val items = examples.getOrPut(key) { mutableListOf() }
            if (items.none { it.example == item.example && it.description == item.description }) {
                items += item
            }
        }
    }

    fun match(query: String, limit: Int = 20): List<PluginCommandExample> {
        val normalizedQuery = query.trim().lowercase()
        val normalizedLimit = limit.coerceIn(0, 100)
        if (normalizedLimit == 0) return emptyList()
        return synchronized(lock) {
            examples.values.asSequence()
                .flatten()
                .filter { item ->
                    normalizedQuery.isBlank() ||
                            item.example.lowercase().contains(normalizedQuery) ||
                            item.description.lowercase().contains(normalizedQuery) ||
                            item.pluginId.lowercase().contains(normalizedQuery) ||
                            item.extensionName.lowercase().contains(normalizedQuery)
                }
                .take(normalizedLimit)
                .toList()
        }
    }

    fun removeExtension(pluginId: String, extensionName: String) {
        synchronized(lock) {
            examples.remove(key(pluginId, extensionName))
        }
    }

    fun removePlugin(pluginId: String) {
        synchronized(lock) {
            examples.keys.removeIf { it.startsWith("$pluginId:") }
        }
    }

    fun clear() {
        synchronized(lock) {
            examples.clear()
        }
    }

    private fun key(pluginId: String, extensionName: String): String = "$pluginId:$extensionName"
}
