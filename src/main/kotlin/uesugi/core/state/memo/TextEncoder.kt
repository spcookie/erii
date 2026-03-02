package uesugi.core.state.memo

import uesugi.toolkit.EmbeddingUtil

/**
 * 简单的文本编码器
 * TODO: 接入真正的embedding模型
 *
 * 当前实现：将文本hash后转换为固定维度向量（仅用于占位）
 */
object TextEncoder {
    /**
     * 将文本编码为向量
     */
    suspend fun encode(text: String): FloatArray {
        val vector = EmbeddingUtil.embedding(text)
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
        } catch (e: Exception) {
            null
        }
    }
}
