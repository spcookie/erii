package uesugi.core.state.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import uesugi.core.component.VectorStore
import uesugi.toolkit.EmbeddingUtil
import java.nio.file.Paths

/**
 * 事实向量存储工厂
 */
class FactVectorStore {
    companion object {
        private const val DIMENSION = 1024
    }

    private val stores = mutableMapOf<String, VectorStore>()

    /**
     * 获取指定 botId 和 groupId 的向量存储
     */
    fun getStore(botMark: String, groupId: String): VectorStore {
        val key = "${botMark}_$groupId"
        return stores.getOrPut(key) {
            val path = Paths.get("./store/vector/fact/$key")
            GlobalContext.get().get { parametersOf(path, DIMENSION) }
        }
    }

    /**
     * 索引事实记忆
     * @return 向量ID
     */
    suspend fun indexFact(fact: FactsRecord): String {
        val botMark = fact.botMark
        val groupId = fact.groupId
        val factId = fact.id

        val vectorId = generateVectorId(botMark, groupId, factId)

        // 组合事实内容用于向量编码
        val content = buildString {
            append(fact.keyword)
            append(" ")
            append(fact.description)
            if (fact.values.isNotBlank()) {
                append(" ")
                append(fact.values)
            }
            if (fact.subjects.isNotBlank()) {
                append(" ")
                append(fact.subjects)
            }
        }

        val vector: FloatArray = withContext(Dispatchers.IO) {
            EmbeddingUtil.embedding(content, null)
        }
        val store = getStore(botMark, groupId)
        store.upsert(vectorId, content, fact.scopeType.name, vector)

        return vectorId
    }

    /**
     * 搜索事实记忆
     */
    suspend fun search(
        query: String,
        groupId: String,
        botMark: String,
        topK: Int
    ): List<FactSearchResult> {
        val vector: FloatArray = withContext(Dispatchers.IO) {
            EmbeddingUtil.embedding(query, null)
        }
        val store = getStore(botMark, groupId)
        val results = store.search(vector, topK)

        return results.map { result ->
            val factId = extractFactId(result.id)
            FactSearchResult(
                vectorId = result.id,
                factId = factId,
                content = result.content,
                score = result.score
            )
        }
    }

    /**
     * 删除向量
     */
    fun deleteVector(vectorId: String, botMark: String, groupId: String) {
        val store = getStore(botMark, groupId)
        store.delete(vectorId)
    }

    /**
     * 生成向量ID
     */
    fun generateVectorId(botMark: String, groupId: String, factId: Int): String {
        return "fact_${botMark}_${groupId}_$factId"
    }

    /**
     * 从向量ID中提取 factId
     * 向量ID格式: fact_{botId}_{groupId}_{factId}
     */
    fun extractFactId(vectorId: String): Int? {
        return try {
            vectorId.substringAfterLast("_").toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * 事实搜索结果
 */
@Serializable
data class FactSearchResult(
    val vectorId: String,
    val factId: Int?,
    val content: String,
    val score: Float
)
