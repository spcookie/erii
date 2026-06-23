package uesugi.core.component.search

import com.fasterxml.jackson.databind.JsonNode
import com.google.auto.service.AutoService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import uesugi.common.extend.ISearch
import uesugi.common.extend.SearchResultItem
import uesugi.common.toolkit.ConfigHolder
import uesugi.common.toolkit.logger

@AutoService(ISearch::class)
class ArkSearch : ISearch {

    override val id: String = "ark"

    private val log = logger()

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson()
        }
    }

    override suspend fun search(query: String?, urls: List<String>?, maxResult: Int): List<SearchResultItem> {
        try {
            val items = mutableListOf<SearchResultItem>()
            val searchUrl = ConfigHolder.getSearchUrl()
            val apiKey = ConfigHolder.getSearchApiKey()
            val model = ConfigHolder.getSearchModel()

            if (!query.isNullOrBlank()) {
                val requestBody = mapOf(
                    "model" to model,
                    "tools" to listOf(
                        mapOf("type" to "web_search")
                    ),
                    "input" to listOf(
                        mapOf(
                            "role" to "user",
                            "content" to listOf(
                                mapOf(
                                    "type" to "input_text",
                                    "text" to query
                                )
                            )
                        )
                    )
                )

                val root: JsonNode = httpClient.post(searchUrl) {
                    contentType(ContentType.Application.Json)
                    bearerAuth(apiKey)
                    setBody(requestBody)
                }.body()

                convert(root, items)
            }

            if (!urls.isNullOrEmpty()) {
                log.warn("Ark search does not support URL content fetching, ignoring urls: $urls")
            }

            return items.take(maxResult)
        } catch (e: Exception) {
            log.error("Ark search failed: ${e.message}", e)
            return emptyList()
        }
    }

    private fun convert(root: JsonNode, items: MutableList<SearchResultItem>) {
        val output = root.path("output")
        for (item in output) {
            if (item.path("type").asText() != "message") continue
            val content = item.path("content")
            for (block in content) {
                val text = block.path("text").asText()
                val annotations = block.path("annotations")
                if (annotations.isArray && annotations.size() > 0) {
                    for (annotation in annotations) {
                        val annotationType = annotation.path("type").asText()
                        if (annotationType == "url_citation") {
                            items.add(
                                SearchResultItem(
                                    url = annotation.path("url").asText(),
                                    title = annotation.path("title").asText(),
                                    content = text.take(3000),
                                    score = 0.8
                                )
                            )
                        }
                    }
                }
                // Fallback: treat the whole text block as a single result
                if (annotations.isMissingNode || !annotations.isArray || annotations.size() == 0) {
                    if (text.isNotBlank()) {
                        items.add(
                            SearchResultItem(
                                url = "",
                                title = "搜索: ${text.take(50)}...",
                                content = text.take(3000),
                                score = 0.5
                            )
                        )
                    }
                }
            }
        }
    }
}
