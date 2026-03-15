package uesugi.toolkit


import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * SearXNG 搜索客户端
 *
 * @param baseUrl SearXNG 的地址
 */
class WebSearchClient(private val baseUrl: String) : AutoCloseable {

    companion object {
        private val log = logger()
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(JSON)
        }
    }

    /**
     * 执行搜索
     *
     * @param query 搜索关键词
     * @param maxResults 最大结果条数 (截断)
     * @param minScore 分数下限 (低于此分数的会被丢弃)
     * @param language 语言 (默认 zh-CN)
     * @param categories 分类 (默认 general)
     * @param safeSearch 安全搜索 (0=关, 1=中, 2=严)
     */
    suspend fun search(
        query: String,
        maxResults: Int = 10,
        minScore: Double = 0.1,
        language: String = "zh-CN",
        categories: String = "general",
        safeSearch: Int = 0
    ): SearchResultAggregate {

        val validResults = mutableListOf<SearchResultItem>()
        val validInfoboxes = mutableListOf<InfoboxItem>()

        var currentPage = 1
        val maxPageDepth = 5 // 安全熔断

        while (validResults.size < maxResults && currentPage <= maxPageDepth) {
            try {
                // 1. 发起请求
                val response: RawSearchResponse = httpClient.get("$baseUrl/search") {
                    parameter("q", query)
                    parameter("language", language)
                    parameter("safesearch", safeSearch)
                    parameter("categories", categories)
                    parameter("format", "json")
                    parameter("pageno", currentPage)
                }.body()

                // 2. 提取 Infobox (通常只在第1页出现，但我们累加去重)
                if (currentPage == 1) {
                    response.infoboxes?.let { boxes ->
                        validInfoboxes.addAll(boxes.map { it.toDomain() })
                    }
                }

                // 3. 提取并过滤 Result
                val pageResults = response.results
                    ?.filter { (it.score ?: 0.0) >= minScore } // 过滤分数
                    ?.map { it.toDomain() }
                    ?: emptyList()

                // 如果这一页完全没东西，说明后面也没了，直接跳出
                if (response.results.isNullOrEmpty()) {
                    break
                }

                validResults.addAll(pageResults)

                // 如果当前页过滤后的结果为空，但 API 实际返回了数据(只是分数低)，
                // 我们还是需要继续翻页试图寻找高质量结果

                currentPage++

            } catch (e: Exception) {
                log.error("Search failed on page $currentPage: ${e.message}", e)
                break
            }
        }

        return SearchResultAggregate(
            results = validResults.take(maxResults), // 截断到最大条数
            infoBoxes = validInfoboxes
        )
    }

    override fun close() {
        httpClient.close()
    }
}

/**
 * 最终返回给调用者的聚合对象
 */
data class SearchResultAggregate(
    val results: List<SearchResultItem>,
    val infoBoxes: List<InfoboxItem>
)

data class SearchResultItem(
    val url: String,
    val title: String,
    val content: String,
    val score: Double
)

data class InfoboxItem(
    val infobox: String,
    val id: String,
    val content: String,
    val imgSrc: String?
)

@Serializable
internal data class RawSearchResponse(
    val query: String? = null,
    val results: List<RawResult>? = null,
    val infoboxes: List<RawInfobox>? = null
)

@Serializable
internal data class RawResult(
    val url: String? = null,
    val title: String? = null,
    val content: String? = null,
    val score: Double? = 0.0
) {
    fun toDomain(): SearchResultItem {
        return SearchResultItem(
            url = this.url ?: "",
            title = this.title ?: "No Title",
            content = this.content ?: "",
            score = this.score ?: 0.0
        )
    }
}

@Serializable
internal data class RawInfobox(
    val infobox: String? = null,
    val id: String? = null,
    val content: String? = null,
    @SerialName("img_src") val imgSrc: String? = null // 映射 snake_case
) {
    fun toDomain(): InfoboxItem {
        return InfoboxItem(
            infobox = this.infobox ?: "",
            id = this.id ?: "",
            content = this.content ?: "",
            imgSrc = this.imgSrc
        )
    }
}


object ExaSearch {

    private val log = logger()

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson()
        }
    }

    suspend fun search(query: String? = null, urls: List<String>? = null, maxResult: Int = 10): List<SearchResultItem> {
        try {
            val items = mutableListOf<SearchResultItem>()
            if (!query.isNullOrBlank()) {
                val root: JsonNode = httpClient.post("https://api.exa.ai/search") {
                    contentType(ContentType.Application.Json)
                    header("x-api-key", System.getenv("EXA_API_KEY"))
                    setBody(
                        mapOf(
                            "query" to query,
                            "numResults" to if (maxResult <= 10) maxResult else 10,
                            "type" to "auto",
                            "userLocation" to "CN",
                            "contents" to mapOf(
                                "highlights" to mapOf(
                                    "maxCharacters" to 3000
                                )
                            )
                        )
                    )
                }.body()
                for (result in root.path("results")) {
                    val item = SearchResultItem(
                        url = result.path("url").asText(),
                        title = result.path("title").asText(),
                        content = result.path("highlights").path(0).asText(),
                        score = result.path("highlightScores").path(0).asDouble()
                    )
                    items.add(item)
                }
            }

            if (!urls.isNullOrEmpty()) {
                val root: JsonNode = httpClient.post("https://api.exa.ai/contents") {
                    contentType(ContentType.Application.Json)
                    header("x-api-key", System.getenv("EXA_API_KEY"))
                    setBody(
                        mapOf(
                            "ids" to urls,
                            "highlights" to mapOf(
                                "maxCharacters" to 3000
                            )
                        )
                    )
                }.body()
                for (result in root.path("results")) {
                    val item = SearchResultItem(
                        url = result.path("url").asText(),
                        title = result.path("title").asText(),
                        content = result.path("highlights").path(0).asText(),
                        score = result.path("highlightScores").path(0).asDouble()
                    )
                    items.add(item)
                }
            }

            return items
        } catch (e: Exception) {
            log.error("Search failed: ${e.message}", e)
            return emptyList()
        }
    }
}