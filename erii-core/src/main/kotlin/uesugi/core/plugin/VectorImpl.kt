package uesugi.core.plugin

import okio.Path.Companion.toPath
import uesugi.core.component.EmbeddedVectorStore
import uesugi.spi.PluginDef
import uesugi.spi.Vector
import uesugi.toolkit.EmbeddingUtil

internal class VectorImpl(val defined: PluginDef) : Vector {

    private val default by lazy {
        EmbeddedVectorStore(
            path = "./store/vector/plugins".toPath().resolve(defined.name).toNioPath(),
            dimension = 1024
        )
    }

    override suspend fun embedding(input: List<String>, images: List<ByteArray>): FloatArray {
        return EmbeddingUtil.embedding(input, images).first()
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
        default.close()
    }
}
