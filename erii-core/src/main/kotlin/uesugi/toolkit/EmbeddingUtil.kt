package uesugi.toolkit

import cn.hutool.core.io.FileTypeUtil
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import uesugi.common.ConfigHolder
import uesugi.config.HttpClientFactory
import java.io.IOException
import kotlin.io.encoding.Base64

object EmbeddingUtil {
    private val client = HttpClientFactory().createClient()

    suspend fun embedding(input: String, image: ByteArray?): FloatArray {
        return embedding(listOf(input), if (image != null) listOf(image) else emptyList()).first()
    }

    suspend fun embedding(input: List<String>, images: List<ByteArray>): List<FloatArray> {
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
