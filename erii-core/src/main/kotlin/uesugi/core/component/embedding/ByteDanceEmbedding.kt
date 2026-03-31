package uesugi.core.component.embedding

import cn.hutool.core.io.FileTypeUtil
import com.fasterxml.jackson.databind.JsonNode
import com.google.auto.service.AutoService
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import uesugi.common.ConfigHolder
import uesugi.common.EmbeddingInput
import uesugi.common.IEmbedding
import uesugi.config.HttpClientFactory
import java.io.IOException
import kotlin.io.encoding.Base64

@AutoService(IEmbedding::class)
class ByteDanceEmbedding : IEmbedding {

    override val id: String = "bytedance"

    companion object {
        private val client = HttpClientFactory().createClient()
    }

    override suspend fun embedding(texts: List<String>): List<FloatArray> {
        return embeddingInternal(texts, emptyList())
    }

    override suspend fun embeddingMultiModal(inputs: List<EmbeddingInput>): List<FloatArray> {
        val texts = inputs.map { it.text }
        val images = inputs.flatMap { it.images }
        return embeddingInternal(texts, images)
    }

    private suspend fun embeddingInternal(input: List<String>, images: List<ByteArray>): List<FloatArray> {
        val node: JsonNode = client.post("https://ark.cn-beijing.volces.com/api/v3/embeddings/multimodal") {
            contentType(ContentType.Application.Json)
            bearerAuth(ConfigHolder.getEmbeddingApiKey())
            val text = input.map {
                mapOf("type" to "text", "text" to it)
            }
            val image = images.map {
                val base64 = Base64.encode(it)
                val mimeType = it.inputStream().use { stream ->
                    when (val type = FileTypeUtil.getType(stream)) {
                        "png" -> "image/png"
                        "jpg", "jpeg" -> "image/jpeg"
                        "gif" -> "image/gif"
                        else -> throw IllegalArgumentException("Unsupported image type: $type")
                    }
                }
                val url = "data:$mimeType;base64,$base64"
                mapOf("type" to "image_url", "image_url" to mapOf("url" to url))
            }
            setBody(
                mapOf(
                    "input" to text + image,
                    "model" to "doubao-embedding-vision-250615",
                    "dimensions" to 1024
                )
            )
        }.body()

        if (node.has("error")) {
            throw IOException(node.path("error").toString())
        }

        return buildList {
            for (embedding in node.path("data")) {
                add(embedding.map { it.floatValue() }.toFloatArray())
            }
        }
    }
}
