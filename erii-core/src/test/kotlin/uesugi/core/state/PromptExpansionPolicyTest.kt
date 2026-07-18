package uesugi.core.state

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PromptExpansionPolicyTest {
    @Test
    fun `user profile prompt asks for multi-dimensional longer output`() {
        val source = readSource("memory/MemoryAgent.kt")

        assertFalse(source.contains("20~80字"))
        assertFalse(source.contains("10~80字"))
        assertTrue(source.contains("120~260字"))
        assertTrue(source.contains("80~180字"))
        assertTrue(source.contains("分维度描述"))
    }

    @Test
    fun `summary prompt asks for richer content and more key points`() {
        val source = readSource("summary/SummaryAgent.kt")

        assertFalse(source.contains("字数控制在150字左右"))
        assertFalse(source.contains("提取3-5个"))
        assertTrue(source.contains("300~600字"))
        assertTrue(source.contains("提取5-8个"))
        assertTrue(source.contains("保留具体人物、话题、争议点、结论和后续悬而未决的问题"))
    }

    private fun readSource(relativePath: String): String {
        val path = Path.of(
            "src",
            "main",
            "kotlin",
            "uesugi",
            "core",
            "state",
            relativePath
        )
        return Files.readString(path)
    }
}
