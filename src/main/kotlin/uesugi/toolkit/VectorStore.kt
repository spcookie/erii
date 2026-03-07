package uesugi.toolkit

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.search.*
import org.apache.lucene.store.FSDirectory
import java.io.Closeable
import java.nio.file.Path
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

data class VectorSearchResult(
    val id: String,
    val content: String,
    val score: Float
)

interface VectorStore {
    fun upsert(id: String, content: String, tag: String, vector: FloatArray)
    fun delete(id: String)
    fun search(queryVector: FloatArray, topK: Int, filter: Map<String, String>? = null): List<VectorSearchResult>
}

class EmbeddedVectorStore(
    path: Path,
    private val dimension: Int
) : VectorStore, Closeable {

    private val directory = FSDirectory.open(path)

    private val writer: IndexWriter
    private val searcherManager: SearcherManager

    private val lock = ReentrantReadWriteLock()

    companion object {
        const val VECTOR_FIELD = "vector"
        const val ID_FIELD = "id"
        const val CONTENT_FIELD = "content"
        const val TAG = "tag"
    }

    init {
        val config = IndexWriterConfig(StandardAnalyzer()).apply {
            openMode = IndexWriterConfig.OpenMode.CREATE_OR_APPEND
        }

        writer = IndexWriter(directory, config)
        searcherManager = SearcherManager(writer, null)
    }

    override fun upsert(id: String, content: String, tag: String, vector: FloatArray) {
        validate(vector)

        lock.write {
            val doc = Document().apply {
                add(StringField(ID_FIELD, id, Field.Store.YES))
                add(TextField(CONTENT_FIELD, content, Field.Store.YES))
                add(StringField(TAG, tag, Field.Store.YES))
                add(KnnFloatVectorField(VECTOR_FIELD, vector))
            }

            writer.updateDocument(Term(ID_FIELD, id), doc)
            writer.commit()
            searcherManager.maybeRefresh()
        }
    }

    override fun delete(id: String) {
        lock.write {
            writer.deleteDocuments(Term(ID_FIELD, id))
            writer.commit()
            searcherManager.maybeRefresh()
        }
    }

    override fun search(
        queryVector: FloatArray,
        topK: Int,
        filter: Map<String, String>?
    ): List<VectorSearchResult> {

        validate(queryVector)

        lock.read {
            val searcher = searcherManager.acquire()

            val knnQuery = KnnFloatVectorQuery(VECTOR_FIELD, queryVector, topK)

            val finalQuery = if (filter.isNullOrEmpty()) {
                knnQuery
            } else {
                buildFilteredQuery(knnQuery, filter)
            }

            val topDocs = searcher.search(finalQuery, topK)

            val storedFields = searcher.storedFields()

            return topDocs.scoreDocs.map {
                val doc = storedFields.document(it.doc)
                VectorSearchResult(
                    id = doc.get(ID_FIELD),
                    content = doc.get(CONTENT_FIELD),
                    score = it.score
                )
            }
        }
    }

    override fun close() {
        searcherManager.close()
        writer.close()
        directory.close()
    }


    private fun buildFilteredQuery(
        knnQuery: Query,
        filter: Map<String, String>
    ): Query {
        val builder = BooleanQuery.Builder()
        builder.add(knnQuery, BooleanClause.Occur.MUST)

        filter.forEach { (k, v) ->
            builder.add(TermQuery(Term(k, v)), BooleanClause.Occur.FILTER)
        }

        return builder.build()
    }

    private fun validate(vector: FloatArray) {
        require(vector.size == dimension) {
            "Vector dimension must be $dimension but was ${vector.size}"
        }
    }
}