package uesugi.core.component

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.markdown.MarkdownContentBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import uesugi.common.logger
import java.util.*

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

suspend fun appendExaWebPagePrompt(
    builder: MarkdownContentBuilder,
    query: String? = null,
    specificUrls: List<String>?,
    maxResults: Int = 3
) {
    val searchService = ServiceLoader.load(ISearch::class.java).first()
    searchService.search(query, specificUrls, maxResults).also { item ->
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

interface ISearch {
    suspend fun search(query: String? = null, urls: List<String>? = null, maxResult: Int = 10): List<SearchResultItem>
}

data class SearchResultItem(
    val url: String,
    val title: String,
    val content: String,
    val score: Double
)
