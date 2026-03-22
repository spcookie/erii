package uesugi.toolkit

import com.fasterxml.jackson.databind.JsonNode
import com.google.auto.service.AutoService
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import uesugi.common.ConfigHolder
import uesugi.common.logger
import uesugi.core.component.ISearch
import uesugi.core.component.SearchResultItem

@AutoService(ISearch::class)
class ExaSearch : ISearch {

    private val log = logger()

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson()
        }
    }

    override suspend fun search(query: String?, urls: List<String>?, maxResult: Int): List<SearchResultItem> {
        try {
            val items = mutableListOf<SearchResultItem>()
            if (!query.isNullOrBlank()) {
                val root: JsonNode = httpClient.post("https://api.exa.ai/search") {
                    contentType(ContentType.Application.Json)
                    header("x-api-key", ConfigHolder.getExaApiKey())
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
                    header("x-api-key", ConfigHolder.getExaApiKey())
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
