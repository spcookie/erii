package uesugi.routing

import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsageTemplateHeatmapTest {
    @Test
    fun `heatmap uses compact calendar layout`() {
        val template = Path.of("src/main/jte/usage-template.kte").readText()

        assertTrue(template.contains("class=\"heatmap-viewport\""))
        assertTrue(template.contains("const heatmapData = createHeatmapData(startDate, heatmapDataEndDate, dailySeriesData);"))
        assertTrue(template.contains("const heatmapSize = calculateHeatmapSquareSize(startDate, 6);"))
        assertTrue(template.contains("subDomain: {type: 'ghDay', width: heatmapSize, height: heatmapSize, gutter: heatmapGutter"))
        assertTrue(template.contains("Math.min(48, size)"))
        assertTrue(template.contains("const createHeatmapData = (start, end, rows) =>"))
        assertTrue(template.contains("result.push({date: key, value: valuesByDate[key] || 0});"))
        assertTrue(template.contains("const heatmapDomainGutter = heatmapGutter;"))
        assertTrue(template.contains("gutter: heatmapDomainGutter"))
        assertFalse(template.contains("subDomain: { type: 'day', width: 23, height: 14"))
        assertFalse(template.contains("if (dailySeriesData.length > 0)"))
        assertFalse(template.contains("highlight: [new Date()]"))
    }

    @Test
    fun `daily trend only renders the latest week`() {
        val template = Path.of("src/main/jte/usage-template.kte").readText()

        assertTrue(template.contains("const dailyTrendData = createWeeklyTrendData(dailySeriesData);"))
        assertTrue(template.contains("const createWeeklyTrendData = (rows) =>"))
        assertTrue(template.contains("return rows.slice(-7);"))
        assertTrue(template.contains("result.push({date: formatDateKey(addDays(end, -i)), tokens: 0, cost: 0});"))
        assertTrue(
            template.contains("data: dailyTrendData.map(({date}) => date)")
        )
        assertTrue(template.contains("showSymbol: true"))
        assertTrue(
            template.contains("data: dailyTrendData.map(({tokens}) => tokens)")
        )
        assertTrue(template.contains("formatter: formatMonthDay"))
        assertTrue(template.contains("const weeklyTrendHasTokens = hasTokens(dailyTrendData);"))
        assertTrue(template.contains("text: '暂无 Token 消耗'"))
    }

    @Test
    fun `usage template applies requested labels and scene ordering`() {
        val template = Path.of("src/main/jte/usage-template.kte").readText()

        assertTrue(template.contains("vm.hasScopeMeta"))
        assertTrue(template.contains("vm.groupName"))
        assertTrue(template.contains("vm.botName"))
        assertTrue(template.contains("vm.botId"))
        assertTrue(template.contains("vm.groupId"))
        assertTrue(template.contains("class=\"scope-id\""))
        assertFalse(template.contains("BOT "))
        assertFalse(template.contains("GROUP "))
        assertFalse(template.contains(".scope-meta {\n            display: flex;\n            gap: 12px;\n            align-items: center;\n            flex-wrap: wrap;\n            margin-top: 14px;\n            color: var(--grey-3);\n            font-size: 12px;\n            text-transform: uppercase;"))
        assertTrue(template.contains("class=\"icon\""))
        assertTrue(template.contains("INPUT CACHE HIT"))
        assertTrue(template.contains("INPUT CACHE MISS"))
        assertTrue(template.contains("OUTPUT TOKENS"))
        assertTrue(template.contains("HIT / INPUT"))
        assertTrue(template.contains("<span>输入 · 缓存命中</span>"))
        assertTrue(template.contains("<span>输入 · 未命中</span>"))
        assertTrue(template.contains("<span>输出</span>"))
        assertTrue(template.contains("<span>花费</span>"))
        assertTrue(template.contains("<span>缓存命中率</span>"))
        assertFalse(template.contains("今日输入"))
        assertFalse(template.contains("今日输出"))
        assertFalse(template.contains("今日花费"))
        assertFalse(template.contains("今日缓存命中率"))
        assertTrue(template.contains("累计消耗 · Accumulated"))
        assertTrue(template.contains("消耗分布 · Distribution"))
        assertTrue(template.contains("周趋势 · Weekly Trend"))
        assertTrue(template.contains("消耗热力图 · Heatmap"))
        assertTrue(template.contains("const sceneOrder = ['聊天', '搜索', '插件', '记忆', '摘要', '情绪', '心流', '冲动', '偏好', '热词', '表情', '路由', '其他'];"))
        assertTrue(template.contains("const sceneChart = horizontalBarChart('sceneBars', sortSceneRows(sceneBarsData));"))
        assertTrue(
            Regex(
                "legend: \\{\\s+data: \\['输入命中', '输入未命中', '输出'],\\s+orient: 'vertical',\\s+left: 0,\\s+top: 'middle',",
                RegexOption.DOT_MATCHES_ALL
            ).containsMatchIn(template)
        )
        assertTrue(template.contains("grid: {left: 150, right: 38, top: 8, bottom: 24}"))
        assertTrue(template.contains("width: 72,"))
        assertFalse(template.contains("chart-subtitle"))
        assertFalse(template.contains("按 Prompt / 模型分布"))
        assertFalse(template.contains("每日趋势 · Daily Trend"))
        assertFalse(template.contains("每日消耗热力图 · Heatmap"))
    }

    @Test
    fun `charts annotate bars and line points with compact token values`() {
        val template = Path.of("src/main/jte/usage-template.kte").readText()

        assertTrue(template.contains("const suffixes = ['', 'k', 'm', 'b'];"))
        assertTrue(template.contains("const chartRowTotal ="))
        assertTrue(template.contains("const chartTotalLabel ="))
        assertTrue(template.contains("label: chartTotalLabel(rows)"))
        assertTrue(
            Regex(
                "label: \\{\\s+show: true,\\s+position: 'top',\\s+formatter: \\(\\{value}\\) => compactNumber\\(value\\)\\s+}",
                RegexOption.DOT_MATCHES_ALL
            ).containsMatchIn(template)
        )
        assertFalse(template.contains("id=\"sceneTokenTotal\""))
        assertFalse(template.contains("id=\"modelTokenTotal\""))
        assertFalse(template.contains("id=\"weeklyTokenTotal\""))
        assertFalse(template.contains("id=\"heatmapTokenTotal\""))
    }

    @Test
    fun `usage template uses modern source structure without changing chart hooks`() {
        val template = Path.of("src/main/jte/usage-template.kte").readText()

        assertFalse(Regex("(?m)^\\s*var\\s").containsMatchIn(template))
        assertFalse(template.contains("function ("))
        assertTrue(template.contains("<article class=\"chart-panel\">"))
        assertTrue(template.contains("role=\"img\" aria-label=\"场景 Token 消耗图\""))
        assertTrue(template.contains("role=\"img\" aria-label=\"模型 Token 消耗图\""))
        assertTrue(template.contains("role=\"img\" aria-label=\"周 Token 消耗趋势图\""))
        assertTrue(template.contains("role=\"img\" aria-label=\"Token 消耗热力图\""))
        assertFalse(template.contains("<script>lucide.createIcons();</script>"))
        assertFalse(template.contains("style=\"background:"))
    }

    @Test
    fun `usage report matches group status content width without shrinking its canvas`() {
        val template = Path.of("src/main/jte/usage-template.kte").readText()
        val usageCommand = Path.of("src/main/kotlin/uesugi/plugin/builtin/usage/Usage.kt").readText()

        assertFalse(template.contains("width: 1280px;"))
        assertTrue(template.contains("max-width: 1120px;"))
        assertTrue(template.contains("margin: 0 auto;"))
        assertTrue(template.contains("padding: 48px;"))
        assertTrue(usageCommand.contains("width = 1280,"))
        assertFalse(usageCommand.contains("width = 1200,"))
    }
}
