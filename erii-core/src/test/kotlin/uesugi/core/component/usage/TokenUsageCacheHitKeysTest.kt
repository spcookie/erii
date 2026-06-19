package uesugi.core.component.usage

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenUsageCacheHitKeysTest {
    @Test
    fun `preferred cache hit token names are centralized`() {
        assertTrue("cached_tokens" in TokenUsageCacheHitKeys.PREFERRED_NAMES)
        assertTrue("cached_content_token_count" in TokenUsageCacheHitKeys.PREFERRED_NAMES)
        assertTrue("cache_hit" in TokenUsageCacheHitKeys.PREFERRED_NAMES)
    }

    @Test
    fun `preferred cache hit token names match snake case and camel case`() {
        assertTrue(TokenUsageCacheHitKeys.matchesPreferred("cached_tokens"))
        assertTrue(TokenUsageCacheHitKeys.matchesPreferred("cachedTokens"))
        assertTrue(TokenUsageCacheHitKeys.matchesPreferred("cache_read_input_tokens"))
        assertTrue(TokenUsageCacheHitKeys.matchesPreferred("cacheReadInputTokens"))
        assertTrue(TokenUsageCacheHitKeys.matchesPreferred("cached_content_token_count"))
        assertTrue(TokenUsageCacheHitKeys.matchesPreferred("cachedContentTokenCount"))
        assertTrue(TokenUsageCacheHitKeys.matchesPreferred("promptCacheHitTokens"))
    }

    @Test
    fun `fallback cache hit token names match snake case and camel case`() {
        assertTrue(TokenUsageCacheHitKeys.matchesFallback("cache_read_tokens"))
        assertTrue(TokenUsageCacheHitKeys.matchesFallback("cacheReadTokens"))
        assertTrue(TokenUsageCacheHitKeys.matchesFallback("cachedContentTokenCount"))
        assertFalse(TokenUsageCacheHitKeys.matchesFallback("input_tokens"))
    }

    @Test
    fun `scene aliases are not normalized before prompt mapping`() {
        val source = Path.of("src/main/kotlin/uesugi/core/component/usage/TokenUsageRepository.kt").readText()

        assertFalse(source.contains("fun normalizeScene"))
        assertFalse(source.contains("\"BotAgent\" -> \"聊天\""))
        assertFalse(source.contains("\"搜索分析\", \"分析\" -> \"搜索\""))
        assertFalse(source.contains("\"记忆提取\", \"记忆冲突\" -> \"记忆\""))
        assertTrue(source.contains("private fun resolveScene(scene: String): String = sceneFor(scene)"))
    }

    @Test
    fun `routing usage has a dedicated scene`() {
        val repositorySource = Path.of("src/main/kotlin/uesugi/core/component/usage/TokenUsageRepository.kt").readText()
        val routingSource = Path.of("src/main/kotlin/uesugi/core/route/RoutingAgent.kt").readText()

        assertTrue(routingSource.contains("prompt(\"__route__\""))
        assertFalse(routingSource.contains("prompt(\"__other__\""))
        assertTrue(repositorySource.contains("\"route\" -> \"路由\""))
        assertTrue(
            Regex(
                "setOf\\(\\s+\"插件\",\\s+\"路由\",\\s+\"聊天\"",
                RegexOption.DOT_MATCHES_ALL
            ).containsMatchIn(repositorySource)
        )
    }
}
