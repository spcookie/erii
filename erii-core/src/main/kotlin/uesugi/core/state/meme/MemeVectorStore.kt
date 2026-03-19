package uesugi.core.state.meme

import org.koin.core.context.GlobalContext
import org.koin.core.parameter.parametersOf
import uesugi.core.component.VectorStore
import uesugi.toolkit.EmbeddingUtil
import java.nio.file.Paths

/**
 * 表情包向量存储工厂
 */
class MemoVectorStore {
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
            val path = Paths.get("./store/vector/meme/$key")
            GlobalContext.get().get { parametersOf(path, DIMENSION) }
        }
    }

    /**
     * 将文本编码为向量
     */
    suspend fun encode(text: String, image: ByteArray? = null): FloatArray {
        val vector = EmbeddingUtil.embedding(text, image)
        return vector
    }

    /**
     * 生成向量ID
     */
    fun generateVectorId(botMark: String, groupId: String, memoId: Int): String {
        return "memo_${botMark}_${groupId}_$memoId"
    }

    /**
     * 从向量ID中提取memoId
     * 向量ID格式: memo_{botId}_{groupId}_{memoId}
     */
    fun extractMemoId(vectorId: String): Int? {
        return try {
            // 从最后一个_之后获取数字
            vectorId.substringAfterLast("_").toIntOrNull()
        } catch (_: Exception) {
            null
        }
    }

}
