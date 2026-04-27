package uesugi.common.extend

/**
 * Embedding 输入数据类，支持多模态
 */
data class EmbeddingInput(
    val text: String,
    val images: List<ByteArray> = emptyList()
)

/**
 * Embedding 服务接口
 * 提供文本和多模态向量化能力
 */
interface IEmbedding {
    val id: String

    /**
     * 对文本列表进行向量化
     */
    suspend fun embedding(texts: List<String>): List<FloatArray>

    /**
     * 对多模态输入进行向量化
     */
    suspend fun embeddingMultiModal(inputs: List<EmbeddingInput>): List<FloatArray>
}
