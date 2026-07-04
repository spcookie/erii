package uesugi.config

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigHolderImplTest {
    @Test
    fun `embedding model has default when local config omits it`() {
        withConfig(
            """
            embedding {
              api-key = "test"
              url = "https://example.test/embeddings"
              provider = "ark"
            }
            """.trimIndent()
        ) {
            assertEquals("doubao-embedding-vision-251215", ConfigHolderImpl().getEmbeddingModel())
        }
    }

    @Test
    fun `embedding model can be overridden by local config`() {
        withConfig(
            """
            embedding {
              api-key = "test"
              url = "https://example.test/embeddings"
              provider = "ark"
              model = "custom-embedding-model"
            }
            """.trimIndent()
        ) {
            assertEquals("custom-embedding-model", ConfigHolderImpl().getEmbeddingModel())
        }
    }

    private fun withConfig(content: String, block: () -> Unit) {
        val previous = System.getProperty("config.path")
        val config = Files.createTempFile("erii-config", ".conf")
        Files.writeString(config, content)
        try {
            System.setProperty("config.path", config.toString())
            block()
        } finally {
            if (previous == null) {
                System.clearProperty("config.path")
            } else {
                System.setProperty("config.path", previous)
            }
            Files.deleteIfExists(config)
        }
    }
}
