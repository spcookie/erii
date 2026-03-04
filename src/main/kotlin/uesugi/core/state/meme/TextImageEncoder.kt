package uesugi.core.state.meme

import uesugi.toolkit.EmbeddingUtil

/**
 * 简单的文本、图片编码器
 *
 */
object TextImageEncoder {
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
