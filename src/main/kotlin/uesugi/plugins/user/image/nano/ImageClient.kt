package uesugi.plugins.user.image.nano

import com.google.genai.Client
import com.google.genai.types.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.InputStream
import javax.imageio.ImageIO
import kotlin.jvm.optionals.getOrElse
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit

class ImageClient : Closeable {

    private var client = Client.builder()
        .httpOptions(
            HttpOptions.builder()
                .timeout(5.minutes.toInt(DurationUnit.MILLISECONDS))
                .build()
        )
        .vertexAI(false)
        .apiKey(System.getenv("GOOGLE_API_KEY"))
        .build()

    fun generate(
        contentParts: List<ContentPart>,
        aspectRatio: String? = null,
        system: String? = null,
        temperature: Float = 1f,
        maxOutputTokens: Int = 32768,
        topP: Float = 1f,
        imageSize: String = "1K",
        modelType: String = "BASIC",
    ): Pair<Pair<String?, BufferedImage?>, GenerateContentResponseUsageMetadata> {
        val contentConfig = GenerateContentConfig.builder()
            .responseModalities("TEXT", "IMAGE")
            .apply {
                if (system != null) {
                    systemInstruction(
                        Content.builder()
                            .parts(
                                Part.builder()
                                    .text(system)
                                    .build()
                            )
                            .build()
                    )
                }
            }
            .temperature(temperature)
            .topP(topP)
            .maxOutputTokens(maxOutputTokens)
            .imageConfig(
                ImageConfig.builder()
                    .apply {
                        if (aspectRatio != null) {
                            aspectRatio(aspectRatio)
                        }
                        if (modelType.uppercase() != "BASIC") {
                            imageSize(imageSize)
                        }
                    }
                    .build()
            )
            .apply {
                if (modelType.uppercase() != "BASIC") {
                    tools(
                        Tool.builder()
                            .googleSearch(GoogleSearch.builder().build())
                            .build()
                    )
                }
            }
            .build()
        val contentParts: List<Part> = try {
            val parts = buildList {
                for ((content, type) in contentParts) {
                    when (type) {
                        ContentPart.Type.TEXT -> {
                            add(Part.fromText(content))
                        }

                        ContentPart.Type.IMAGE -> {
                            val file = client.files.get(content, GetFileConfig.builder().build())
                            add(Part.fromUri(file.uri().get(), file.mimeType().get()))
                        }
                    }
                }

            }
            parts
        } catch (_: Exception) {
            buildList {
                for ((content, type) in contentParts) {
                    if (type == ContentPart.Type.TEXT) {
                        add(
                            Part.builder().text(content)
                                .build()
                        )
                    }
                }
            }
        }
        val response: GenerateContentResponse = try {
            val contentObj = Content.fromParts(*contentParts.toTypedArray())
            client.models.generateContent(
                when (modelType.uppercase()) {
                    "BASIC" -> "gemini-2.5-flash-image"
                    else -> "gemini-3-pro-image-preview"
                },
                contentObj,
                contentConfig
            )
        } finally {
        }
        val result = response.candidates()
            .map { candidates ->
                candidates.stream()
                    .map { candidate ->
                        candidate.content()
                            .flatMap { content -> content.parts() }
                            .getOrElse { listOf() }
                    }
                    .findFirst()
                    .getOrElse { listOf() }
            }
            .map { parts ->
                var text: String? = null
                var image: BufferedImage? = null
                for (part in parts) {
                    if (part.text().isPresent) {
                        text = part.text().get()
                    } else if (part.inlineData().isPresent) {
                        image = ImageIO.read(
                            ByteArrayInputStream(
                                part.inlineData().flatMap { obj -> obj.data() }.get()
                            )
                        )
                    }
                }
                Pair(text, image)
            }
            .getOrElse { Pair(null, null) }
        val usage = response.usageMetadata().get()
        return Pair(result, usage)
    }

    data class UploadedFile(val uri: String, val mimeType: String)

    fun upload(imgStream: InputStream, fileName: String): UploadedFile {
        val mimeType = when (val type = fileName.substringAfterLast(".")) {
            "png" -> "image/png"
            "jpg", "jpeg", "gif" -> "image/jpeg"
            else -> throw IllegalArgumentException("Unsupported image type: $type")
        }
        val file = client.files.upload(
            imgStream.readAllBytes(),
            UploadFileConfig.builder().mimeType(mimeType).build()
        )
        return UploadedFile(file.name().get(), mimeType)
    }

    fun delete(uriOrName: String) {
        try {
            client.files.delete(uriOrName, null)
        } catch (_: Exception) {
        }
    }

    override fun close() {
        client.close()
    }
}
