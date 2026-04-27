package uesugi.core.component.embedding

import uesugi.common.extend.IEmbedding
import uesugi.common.toolkit.ConfigHolder
import java.util.*

object EmbeddingManager {
    private val providers: Map<String, IEmbedding> by lazy {
        ServiceLoader.load(IEmbedding::class.java).associateBy { it.id }
    }

    fun get(): IEmbedding {
        val id = ConfigHolder.getEmbeddingProvider()
        return providers[id] ?: error("No embedding provider found for id: $id, available: ${providers.keys}")
    }
}