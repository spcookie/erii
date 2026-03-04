package uesugi.toolkit

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.markdown.MarkdownContentBuilder
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import uesugi.LOG
import kotlin.random.Random

/**
 * 根据中文打字速度计算发送延迟
 *
 * @param text 要发送的文本
 * @param cpm 字/分钟 (如 80)
 * @param jitter 抖动比例 (0.1 = ±10%)
 */
fun calcHumanTypingDelay(
    text: String,
    cpm: Int = 160,
    jitter: Double = 0.15
): Long {
    val charCount = text.count { !it.isWhitespace() }
    val cps = cpm / 60.0

    val baseDelayMs = (charCount / cps * 1000).toLong()
    val jitterFactor = 1 + Random.nextDouble(-jitter, jitter)

    return (baseDelayMs * jitterFactor).toLong().coerceAtLeast(300)
}


suspend fun appendWebPagePrompt(
    builder: MarkdownContentBuilder,
    query: String? = null,
    specificUrls: List<String>?,
    maxResults: Int = 3
) {
    val webSearchClient by ref<WebSearchClient>()
    val webPageMarkdownScraper by ref<WebPageMarkdownScraper>()

    suspend fun MarkdownContentBuilder.buildContentBySearchResult(searchResults: List<SearchResultItem>) {
        coroutineScope {
            val awaitAll = searchResults.mapIndexed { index, item ->
                async {
                    runCatching {
                        val result = webPageMarkdownScraper.scrape(item.url, 1000)
                        Pair(index, result)
                    }.recover { error ->
                        LOG.error("scrape url failed: {}", error.message, error)
                        Pair(
                            index, ScrapedResult(
                                url = item.url,
                                title = item.title,
                                excerpt = "",
                                markdown = item.content,
                            )
                        )
                    }.getOrThrow()
                }
            }.awaitAll()
            if (awaitAll.isNotEmpty()) {
                awaitAll.sortedBy { it.first }
                    .map { it.second }
                    .forEach { item ->
                        line {
                            text(item.title)
                            text(item.markdown)
                        }
                    }
            } else {
                line {
                    text("暂未搜索到内容")
                }
            }
        }
    }

    builder.apply {
        if (!specificUrls.isNullOrEmpty()) {
            buildContentBySearchResult(specificUrls.map {
                SearchResultItem(
                    url = it,
                    title = "",
                    content = "",
                    score = 1.0
                )
            })
        } else if (!query.isNullOrBlank()) {
            coroutineScope {
                val aggregate = webSearchClient.search(
                    query = query,
                    maxResults = maxResults,
                    minScore = 0.9
                )
                val infoBoxes = aggregate.infoBoxes
                if (infoBoxes.isNotEmpty()) {
                    val item = infoBoxes[0]
                    line { text(item.infobox) }
                    line { text(item.content) }
                }
                buildContentBySearchResult(aggregate.results)
            }
        } else {
            line { text("") }
        }
    }
}

suspend fun appendExaWebPagePrompt(
    builder: MarkdownContentBuilder,
    query: String? = null,
    specificUrls: List<String>?,
    maxResults: Int = 3
) {
    ExaSearch.search(query, specificUrls, maxResults).also { item ->
        builder.apply {
            if (item.isNotEmpty()) {
                item.forEach { item ->
                    line { text(item.title) }
                    line { text(item.content) }
                }
            } else {
                line { text("暂未搜索到内容") }
            }
        }
    }
}

object WebSearchTool : ToolSet {

    private val log = logger()

    @Serializable
    data class Input(
        @property:LLMDescription("搜索关键词。如果提供了 specificUrl，此项可为空。")
        val query: String? = null,
        @property:LLMDescription("需要直接读取内容的特定 URL。直接访问该链接。")
        val specificUrls: List<String>? = null,
        @property:LLMDescription("搜索结果数量 (1-5)。简单事实查询填 1-2；复杂话题研究/对比分析填 3-5。默认为 3。")
        val maxResults: Int? = null
    )

    @Tool
    @LLMDescription(
        """
        全能联网工具，具备【搜索引擎】和【网页阅读器】功能。
        请在以下情况务必调用此工具：
        1. 用户询问最新的新闻、事件、或超出你的训练数据范围的信息。
        2. 需要查询特定事实、数据或进行事实核查，以确保回复的准确性。
        3. 用户提供了具体的 URL 链接并要求总结、分析或读取内容。
        不要猜测，请通过搜索获取最新、最准确的信息来回答。
        **参数使用指南**：
        - 查询简单事实（如“今天几号”、“某人是谁”）：maxResults 设为 1 或 3。
        - 深度调研/总结（如“分析某事件的影响”）：maxResults 设为 4 或 5。
    """
    )
    suspend fun webSearch(input: Input): String {
        val (query, specificUrls, maxResults) = input
        log.info("webSearch query=$query, specificUrls=$specificUrls, maxResults=$maxResults")
        return try {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    MarkdownContentBuilder()
                        .apply {
                            appendExaWebPagePrompt(this, query, specificUrls, maxResults ?: 3)
                        }
                        .build()
                }
            }
        } catch (e: Exception) {
            log.error("webSearch failed: {}", e.message, e)
            "搜索/抓取失败"
        }
    }
}