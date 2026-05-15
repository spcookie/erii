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
class MiniMaxSearch : ISearch {

    override val id: String = "minimax"

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

            if (!query.isNullOrBlank()) {
                val root: JsonNode = httpClient.post(searchUrl) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer ${ConfigHolder.getSearchApiKey()}")
                    setBody(
                        mapOf(
                            "q" to query
                        )
                    )
                }.body()
                convert(root, items)
            }

            if (!urls.isNullOrEmpty()) {
                log.warn("MiniMax search does not support URL content fetching, ignoring urls: $urls")
            }

            return items.take(maxResult)
        } catch (e: Exception) {
            log.error("Search failed: ${e.message}", e)
            return emptyList()
        }
    }

    private fun convert(
        root: JsonNode,
        items: MutableList<SearchResultItem>
    ) {
        val results = root.path("organic")
        for (result in results) {
            val item = SearchResultItem(
                url = result.path("link").asText(),
                title = result.path("title").asText(),
                content = result.path("snippet").asText(),
                score = 0.5
            )
            items.add(item)
        }
    }
}
