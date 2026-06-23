package uesugi.core.component.embedding

import com.fasterxml.jackson.databind.JsonNode
import com.google.auto.service.AutoService
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import uesugi.common.extend.EmbeddingInput
import uesugi.common.extend.IEmbedding
import uesugi.common.toolkit.ConfigHolder
import uesugi.config.HttpClientFactory
import java.io.IOException

@AutoService(IEmbedding::class)
class ArkEmbedding : IEmbedding {

    override val id: String = "ark"

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
        val node: JsonNode = client.post(ConfigHolder.getEmbeddingUrl()) {
            contentType(ContentType.Application.Json)
            bearerAuth(ConfigHolder.getEmbeddingApiKey())
            val text = input.map {
                mapOf("type" to "text", "text" to it)
            }
            val image = images.map {
                val url = it.toDataUrl()
                mapOf("type" to "image_url", "image_url" to mapOf("url" to url))
            }
            setBody(
                mapOf(
                    "input" to text + image,
                    "model" to ConfigHolder.getEmbeddingModel(),
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
