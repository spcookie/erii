package uesugi.routing

import uesugi.core.component.usage.TokenUsageSummary
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsageViewModelTest {
    @Test
    fun `usage view model exposes optional scope metadata`() {
        val scoped = buildUsageViewModel(
            summary = emptySummary(),
            botId = "bot-a",
            botName = "Erii",
            groupId = "10001",
            groupName = "测试群"
        )
        val global = buildUsageViewModel(emptySummary())

        assertTrue(scoped.hasScopeMeta)
        assertFalse(global.hasScopeMeta)
    }

    private fun emptySummary(): TokenUsageSummary = TokenUsageSummary(
        todayCacheHitInput = 0,
        todayCacheMissInput = 0,
        todayOutput = 0,
        todayCost = 0.0,
        priceUnit = "USD",
        totalCacheHitInput = 0,
        totalCacheMissInput = 0,
        totalOutput = 0,
        totalCost = 0.0,
        todayCacheHitRate = 0.0,
        sceneBars = emptyList(),
        modelBars = emptyList(),
        dailySeries = emptyList()
    )
}
