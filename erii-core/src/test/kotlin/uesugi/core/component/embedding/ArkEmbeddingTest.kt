package uesugi.core.component.embedding

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.runBlocking
import uesugi.common.extend.EmbeddingInput
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArkEmbeddingTest {
    private val mapper = ObjectMapper()

    @Test
    fun `embed inputs individually preserves one vector per input`() = runBlocking {
        val inputs = listOf(
            EmbeddingInput("first"),
            EmbeddingInput("second")
        )

        val vectors = embedArkInputsIndividually(inputs) { input ->
            listOf(floatArrayOf(input.text.length.toFloat()))
        }

        assertEquals(2, vectors.size)
        assertContentEquals(floatArrayOf(5f), vectors[0])
        assertContentEquals(floatArrayOf(6f), vectors[1])
    }

    @Test
    fun `parse embedding response extracts vectors from data embedding arrays`() {
        val node = mapper.readTree(
            """
            {
              "data": [
                {
                  "embedding": [0.25, -0.5, 1.0],
                  "index": 0
                }
              ]
            }
            """.trimIndent()
        )

        val vectors = ArkEmbedding.parseEmbeddingResponse(node)

        assertEquals(1, vectors.size)
        assertContentEquals(floatArrayOf(0.25f, -0.5f, 1.0f), vectors.single())
    }

    @Test
    fun `parse embedding response extracts vector from documented data object`() {
        val node = mapper.readTree(
            """
            {
              "created": 1752133360,
              "data": {
                "embedding": [0.25, -0.5, 1.0],
                "object": "embedding"
              },
              "object": "list"
            }
            """.trimIndent()
        )

        val vectors = ArkEmbedding.parseEmbeddingResponse(node)

        assertEquals(1, vectors.size)
        assertContentEquals(floatArrayOf(0.25f, -0.5f, 1.0f), vectors.single())
    }

    @Test
    fun `build embedding request includes documented float encoding format`() {
        val body = buildArkEmbeddingRequestBody(
            input = listOf(ArkEmbeddingInput.Text("hello")),
            model = "doubao-embedding-vision-251215"
        )

        assertEquals("doubao-embedding-vision-251215", body["model"])
        assertEquals("float", body["encoding_format"])
        assertEquals(1024, body["dimensions"])
        assertEquals(listOf(mapOf("type" to "text", "text" to "hello")), body["input"])
    }

    @Test
    fun `parse embedding response extracts vector from root embedding array`() {
        val node = mapper.readTree(
            """
            {
              "embedding": [0.25, -0.5, 1.0],
              "object": "embedding"
            }
            """.trimIndent()
        )

        val vectors = ArkEmbedding.parseEmbeddingResponse(node)

        assertEquals(1, vectors.size)
        assertContentEquals(floatArrayOf(0.25f, -0.5f, 1.0f), vectors.single())
    }

    @Test
    fun `parse embedding response rejects missing embedding arrays`() {
        val node = mapper.readTree(
            """
            {
              "data": [
                {
                  "index": 0
                }
              ]
            }
            """.trimIndent()
        )

        assertFailsWith<IOException> {
            ArkEmbedding.parseEmbeddingResponse(node)
        }
    }
}
