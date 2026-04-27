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
class ExaSearch : ISearch {

    override val id: String = "exa"

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
            val contentsUrl = "$searchUrl/contents"
            if (!query.isNullOrBlank()) {
                val root: JsonNode = httpClient.post(searchUrl) {
                    contentType(ContentType.Application.Json)
                    header("x-api-key", ConfigHolder.getSearchApiKey())
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
                convert(root, items)
            }

            if (!urls.isNullOrEmpty()) {
                val root: JsonNode = httpClient.post(contentsUrl) {
                    contentType(ContentType.Application.Json)
                    header("x-api-key", ConfigHolder.getSearchApiKey())
                    setBody(
                        mapOf(
                            "ids" to urls,
                            "highlights" to mapOf(
                                "maxCharacters" to 3000
                            )
                        )
                    )
                }.body()
                convert(root, items)
            }

            return items
        } catch (e: Exception) {
            log.error("Search failed: ${e.message}", e)
            return emptyList()
        }
    }

    private fun convert(
        root: JsonNode,
        items: MutableList<SearchResultItem>
    ) {
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
}