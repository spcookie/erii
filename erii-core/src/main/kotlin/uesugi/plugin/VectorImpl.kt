package uesugi.plugin

import uesugi.common.extend.EmbeddingInput
import uesugi.config.StorePathConfig
import uesugi.core.component.embedding.EmbeddingManager
import uesugi.core.component.storage.EmbeddedVectorStore
import uesugi.spi.PluginDef
import uesugi.spi.Vector

internal class VectorImpl(val defined: PluginDef) : Vector {

    private val defaultDelegate = lazy {
        EmbeddedVectorStore(
            path = StorePathConfig.resolve("vector", "plugins", defined.name),
            dimension = 1024
        )
    }
    private val default by defaultDelegate

    override suspend fun embedding(input: List<String>, images: List<ByteArray>): FloatArray {
        val inputs = input.mapIndexed { idx, text ->
            EmbeddingInput(text, if (idx == 0) images else emptyList())
        }
        return EmbeddingManager.get().embeddingMultiModal(inputs).first()
    }

    override suspend fun search(
        queryVector: FloatArray,
        topK: Int,
        filter: List<String>?
    ): List<Vector.SearchResult> {
        return default.search(queryVector, topK, filter).map {
            Vector.SearchResult(it.id, it.content, it.tag, it.score)
        }
    }

    override suspend fun upsert(id: String, content: String, tag: String, vector: FloatArray) {
        default.upsert(id, content, tag, vector)
    }

    override suspend fun delete(id: String) {
        default.delete(id)
    }

    override suspend fun deleteAll() {
        default.deleteAll()
    }

    override fun close() {
        if (defaultDelegate.isInitialized()) {
            default.close()
        }
    }
}
