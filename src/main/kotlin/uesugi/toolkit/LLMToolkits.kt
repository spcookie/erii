package uesugi.toolkit

import ai.koog.prompt.markdown.MarkdownContentBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

