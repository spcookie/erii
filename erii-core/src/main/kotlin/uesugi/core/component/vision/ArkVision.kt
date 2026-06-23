package uesugi.core.component.vision

import com.fasterxml.jackson.databind.JsonNode
import com.google.auto.service.AutoService
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import uesugi.common.extend.IVision
import uesugi.common.toolkit.ConfigHolder
import java.io.File
import java.io.IOException
import java.util.*

@AutoService(IVision::class)
class ArkVision : IVision {
    override val id: String
        get() = "ark"

    private val log = KotlinLogging.logger {}

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson()
        }
        engine {
            requestTimeout = 60_000
        }
    }

    private val baseUrl: String
        get() = ConfigHolder.getVisionUrl()

    private val apiKey: String
        get() = ConfigHolder.getVisionApiKey()

    private val model: String
        get() = ConfigHolder.getVisionModel()

    override suspend fun vision(prompt: String, url: String): String {
        val imageData = processImageUrl(url)
        val node: JsonNode = httpClient.post(baseUrl) {
            headers {
                append(HttpHeaders.ContentType, ContentType.Application.Json)
                append(HttpHeaders.Authorization, "Bearer $apiKey")
            }
            setBody(
                mapOf(
                    "model" to model,
                    "input" to listOf(
                        mapOf(
                            "role" to "user",
                            "content" to listOf(
                                mapOf(
                                    "type" to "input_image",
                                    "image_url" to imageData
                                ),
                                mapOf(
                                    "type" to "input_text",
                                    "text" to prompt
                                )
                            )
                        )
                    )
                )
            )
        }.body()

        val output = node.path("output")
        for (item in output) {
            if (item.path("type").asText() == "message") {
                val content = item.path("content")
                for (block in content) {
                    val text = block.path("text").asText()
                    if (text.isNotBlank()) return text
                }
            }
        }

        throw ArkRequestError("Response missing valid output: $node")
    }

    suspend fun processImageUrl(imageUrlInput: String): String {
        var imageUrl = imageUrlInput
        if (imageUrl.startsWith("@")) {
            imageUrl = imageUrl.substring(1)
        }
        if (imageUrl.startsWith("data:")) {
            return imageUrl
        }
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            return downloadFromUrl(imageUrl)
        }
        return readFromFile(imageUrl)
    }

    private suspend fun downloadFromUrl(imageUrl: String): String {
        return runCatching {
            val response = httpClient.get(imageUrl)
            val bytes: ByteArray = response.body()
            val contentType = response.contentType()?.toString()?.lowercase() ?: ""
            val imageFormat = when {
                contentType.contains("jpeg") || contentType.contains("jpg") -> "jpeg"
                contentType.contains("png") -> "png"
                contentType.contains("webp") -> "webp"
                else -> "jpeg"
            }
            val base64 = Base64.getEncoder().encodeToString(bytes)
            "data:image/$imageFormat;base64,$base64"
        }.getOrElse { e ->
            log.error(e) { "Failed to download image from URL: $imageUrl" }
            throw ArkRequestError("Failed to download image from URL: ${e.message}")
        }
    }

    private fun readFromFile(imageUrl: String): String {
        val file = File(imageUrl)
        if (!file.exists()) {
            throw ArkRequestError("Local image file does not exist: $imageUrl")
        }
        return try {
            val imageBytes = file.readBytes()
            val lower = imageUrl.lowercase()
            val imageFormat = when {
                lower.endsWith(".png") -> "png"
                lower.endsWith(".webp") -> "webp"
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "jpeg"
                else -> "jpeg"
            }
            val base64 = Base64.getEncoder().encodeToString(imageBytes)
            "data:image/$imageFormat;base64,$base64"
        } catch (e: IOException) {
            log.error(e) { "Failed to read local image file: $imageUrl" }
            throw ArkRequestError("Failed to read local image file: ${e.message}")
        }
    }
}

class ArkRequestError(message: String) : Exception(message)
