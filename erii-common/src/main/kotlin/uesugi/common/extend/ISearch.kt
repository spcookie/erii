package uesugi.common.extend

interface ISearch {
    val id: String
    suspend fun search(query: String? = null, urls: List<String>? = null, maxResult: Int = 10): List<SearchResultItem>
}

data class SearchResultItem(
    val url: String,
    val title: String,
    val content: String,
    val score: Double
)
