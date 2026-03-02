package uesugi.routing

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import uesugi.core.state.memo.MemoService

fun Routing.configureMemo() {
    authenticate("basic") {
        get("/memo/{query}") {
            val query = call.request.pathVariables["query"] ?: ""
            val memoService: MemoService by inject()
            val records = memoService.searchByVector("2522603045", "1053148332", query, 5)
            call.respond(records.map {
                MemoDto(
                    id = it.first.id,
                    resourceId = it.first.resourceId,
                    description = it.first.description,
                    purpose = it.first.purpose,
                    tags = it.first.tags,
                    score = it.second
                )
            })
        }
    }
}


@Serializable
data class MemoDto(
    val id: Int?,
    val resourceId: Int,
    val description: String?,
    val purpose: String?,
    val tags: String?,
    val score: Float
)