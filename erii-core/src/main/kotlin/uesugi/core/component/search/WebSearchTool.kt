package uesugi.core.component.search

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.model.StructureFixingParser
import ai.koog.prompt.executor.model.executeStructured
import ai.koog.prompt.markdown.MarkdownContentBuilder
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import uesugi.common.LLMProviderChoice
import uesugi.common.extend.SearchResultItem
import uesugi.common.toolkit.logger
import uesugi.common.toolkit.ref
import kotlin.time.ExperimentalTime

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

    /**
     * Query Rewrite 子 Agent 的输出结构
     */
    @Serializable
    data class SearchPlan(
        val queries: List<String>  // 优化后的搜索查询（最多使用前 3 个）
    )

    /**
     * Query Rewrite 子 Agent：将用户原始问题转化为高质量搜索查询
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun rewriteQuery(originalQuery: String): SearchPlan {
        val promptExecutor by ref<PromptExecutor>()

        val rewritePrompt = prompt("query-rewrite") {
            system(
                """
                你是一个搜索查询优化专家。将用户的原始问题转化为 1-3 个高质量搜索引擎查询。
                
                规则：
                1. 提取核心关键词，去除无关词语
                2. 技术问题优先使用英文查询（搜索引擎英文资源覆盖更全）
                3. 模糊问题改写为具体明确的查询
                4. 对比分析类问题拆分为独立查询
                5. 添加有助于精确搜索的限定词（如 "best practice"、"official docs"）
                6. 每个查询简洁有力，不超过 10 个词
                7. 简单事实查询只需 1 个查询，复杂问题 2-3 个
                """.trimIndent()
            )
            user("原始问题：$originalQuery")
        }

        val result = promptExecutor.executeStructured<SearchPlan>(
            prompt = rewritePrompt,
            model = LLMProviderChoice.Lite,
            fixingParser = StructureFixingParser(
                model = LLMProviderChoice.Lite,
                retries = 2
            )
        )

        return result.getOrThrow().data
    }

    /**
     * 构建富格式 Markdown 输出
     */
    private fun buildResultMarkdown(results: List<SearchResultItem>): String {
        return MarkdownContentBuilder().apply {
            if (results.isNotEmpty()) {
                results.forEachIndexed { index, item ->
                    header(3, "${index + 1}. ${item.title}")
                    line { text("**来源**: ${item.url}") }
                    line { text("**相关度**: ${"%.2f".format(item.score)}") }
                    line { text(item.content) }
                }
            } else {
                line { text("暂未搜索到内容") }
            }
        }.build()
    }

    /**
     * 使用 LLM 对搜索结果进行信息融合聚合
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun synthesizeResults(
        originalQuery: String,
        results: List<SearchResultItem>
    ): String {
        val promptExecutor by ref<PromptExecutor>()

        val resultsText = results.mapIndexed { index, item ->
            """
            [${index + 1}] ${item.title}
            URL: ${item.url}
            相关度: ${"%.2f".format(item.score)}
            内容: ${item.content}
            """.trimIndent()
        }.joinToString("\n\n---\n\n")

        val synthesizePrompt = prompt("result-synthesis") {
            system(
                """
                你是一个信息整合专家。根据以下搜索结果，为用户的问题提供一个准确、结构化的综合回答。

                整合规则：
                1. 交叉验证：多个来源一致的信息可信度更高
                2. 优先级：官方文档 > 知名技术社区 > 普通博客
                3. 矛盾处理：如果不同来源信息矛盾，明确指出分歧
                4. 来源标注：关键信息标注来源 URL
                5. 结构化输出：使用清晰的分点/分段组织
                6. 时效性：优先采用最新信息
                7. 不要编造搜索结果中没有的信息
                """.trimIndent()
            )
            user(
                """
                用户问题：$originalQuery

                搜索结果：
                $resultsText
                """.trimIndent()
            )
        }

        val result = promptExecutor.execute(synthesizePrompt, model = LLMProviderChoice.Lite)
        return result.textContent()
            .ifEmpty { throw IllegalStateException("LLM synthesis returned no assistant message") }
    }

    @Tool
    @LLMDescription(
        """
        每当您需要搜索网络上的实时或外部信息时，必须使用此工具。
        一款功能与 Google 搜索完全一致的网页搜索 API。

        搜索策略：
        - 如果未返回有用结果，请尝试使用不同的关键词重新表述您的查询。

        使用方式：
        - query: 直接传入用户的原始问题或意图
        - specificUrls: 用户提供具体 URL 时使用，直接读取网页内容。
        - maxResults: 简单查询 1-2，普通问题 3，深度研究/对比分析 4-5

        结果定义：
        - 每条结果附带来源 URL 和相关度评分（0-1）
        - 评分越高表示与查询越相关，应据此评估信息可信度
    """
    )
    suspend fun webSearch(input: Input): String {
        val (query, specificUrls, maxResults) = input
        log.info("webSearch query=$query, specificUrls=$specificUrls, maxResults=$maxResults")

        return try {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    val effectiveMaxResults = maxResults?.let { if (it > 5) 5 else it } ?: 3

                    // Step 1: Query Rewrite（仅对关键词搜索生效）
                    val searchQueries = if (!query.isNullOrBlank()) {
                        try {
                            val plan = rewriteQuery(query)
                            log.info("Query rewrite: original='$query' -> rewritten=${plan.queries}")
                            plan.queries.take(3)
                        } catch (e: Exception) {
                            log.warn("Query rewrite failed, using original: {}", e.message)
                            listOf(query)
                        }
                    } else {
                        emptyList()
                    }

                    // Step 2: 并行搜索
                    val searchService = SearchManager.get()
                    val allResults = if (searchQueries.isNotEmpty()) {
                        searchQueries.map { q ->
                            async { searchService.search(q, null, effectiveMaxResults) }
                        }.awaitAll().flatten()
                    } else {
                        emptyList()
                    }

                    // URL 直读结果
                    val urlResults = if (!specificUrls.isNullOrEmpty()) {
                        searchService.search(null, specificUrls, effectiveMaxResults)
                    } else {
                        emptyList()
                    }

                    // Step 3: 合并 + 去重 + 排序 + 截取
                    val mergedResults = (allResults + urlResults)
                        .distinctBy { it.url }
                        .sortedByDescending { it.score }
                        .take(effectiveMaxResults)

                    // Step 4: 信息聚合合成
                    // 条件：有关键词搜索结果且结果数 >= 2 时使用 LLM 聚合
                    // 仅 URL 直读或单条结果时直接格式化输出
                    if (!query.isNullOrBlank() && mergedResults.size >= 2) {
                        try {
                            synthesizeResults(query, mergedResults)
                        } catch (e: Exception) {
                            log.warn("Result synthesis failed, falling back to raw results: {}", e.message)
                            buildResultMarkdown(mergedResults)
                        }
                    } else {
                        buildResultMarkdown(mergedResults)
                    }
                }
            }
        } catch (e: Exception) {
            log.error("webSearch failed: {}", e.message, e)
            "搜索/抓取失败"
        }
    }
}