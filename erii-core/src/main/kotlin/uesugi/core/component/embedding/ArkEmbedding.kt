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

        internal fun parseEmbeddingResponse(node: JsonNode): List<FloatArray> =
            buildList {
                val data = node.path("data")
                if (data.isObject) {
                    add(parseEmbeddingArray(data.path("embedding"), "Embedding response data.embedding is missing"))
                    return@buildList
                }

                val rootEmbedding = node.path("embedding")
                if (rootEmbedding.isArray && !rootEmbedding.isEmpty) {
                    add(parseEmbeddingArray(rootEmbedding, "Embedding response embedding is missing"))
                    return@buildList
                }

                if (!data.isArray) {
                    throw IOException("Embedding response data is missing")
                }
                for (item in data) {
                    add(parseEmbeddingArray(item.path("embedding"), "Embedding response item is missing embedding array"))
                }
            }

        private fun parseEmbeddingArray(node: JsonNode, message: String): FloatArray {
            if (!node.isArray || node.isEmpty) {
                throw IOException(message)
            }
            return node.map { it.floatValue() }.toFloatArray()
        }
    }

    override suspend fun embedding(texts: List<String>): List<FloatArray> {
        return embedArkInputsIndividually(texts.map { EmbeddingInput(it) }) { input ->
            embeddingInternal(listOf(input.text), emptyList())
        }
    }

    override suspend fun embeddingMultiModal(inputs: List<EmbeddingInput>): List<FloatArray> {
        return embedArkInputsIndividually(inputs) { input ->
            embeddingInternal(listOf(input.text), input.images)
        }
    }

    private suspend fun embeddingInternal(input: List<String>, images: List<ByteArray>): List<FloatArray> {
        val node: JsonNode = client.post(ConfigHolder.getEmbeddingUrl()) {
            contentType(ContentType.Application.Json)
            bearerAuth(ConfigHolder.getEmbeddingApiKey())
            val text = input.map { ArkEmbeddingInput.Text(it) }
            val image = images.map { ArkEmbeddingInput.ImageUrl(it.toDataUrl()) }
            setBody(
                buildArkEmbeddingRequestBody(
                    input = text + image,
                    model = ConfigHolder.getEmbeddingModel()
                )
            )
        }.body()

        if (node.has("error")) {
            throw IOException(node.path("error").toString())
        }

        return parseEmbeddingResponse(node)
    }
}

internal suspend fun embedArkInputsIndividually(
    inputs: List<EmbeddingInput>,
    embedOne: suspend (EmbeddingInput) -> List<FloatArray>
): List<FloatArray> =
    inputs.map { input ->
        val vectors = embedOne(input)
        check(vectors.size == 1) {
            "Expected 1 embedding vector but got ${vectors.size}"
        }
        vectors.single()
    }

internal sealed class ArkEmbeddingInput {
    abstract fun toRequestPart(): Map<String, Any>

    data class Text(val text: String) : ArkEmbeddingInput() {
        override fun toRequestPart(): Map<String, Any> =
            mapOf("type" to "text", "text" to text)
    }

    data class ImageUrl(val url: String) : ArkEmbeddingInput() {
        override fun toRequestPart(): Map<String, Any> =
            mapOf("type" to "image_url", "image_url" to mapOf("url" to url))
    }
}

internal fun buildArkEmbeddingRequestBody(
    input: List<ArkEmbeddingInput>,
    model: String
): Map<String, Any> =
    mapOf(
        "model" to model,
        "encoding_format" to "float",
        "input" to input.map { it.toRequestPart() },
        "dimensions" to 1024
    )
