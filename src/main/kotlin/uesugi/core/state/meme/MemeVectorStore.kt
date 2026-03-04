package uesugi.core.state.meme

import uesugi.toolkit.EmbeddedVectorStore
import uesugi.toolkit.VectorStore
import java.nio.file.Paths

/**
 * 表情包向量存储工厂
 */
class MemoVectorStoreFactory {
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
            EmbeddedVectorStore(path, DIMENSION)
        }
    }

}
