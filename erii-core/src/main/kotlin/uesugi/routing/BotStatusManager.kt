package uesugi.routing

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import uesugi.core.state.evolution.EvolutionService
import uesugi.core.state.evolution.SlangWord
import uesugi.core.state.evolution.toRecord
import uesugi.core.state.meme.MemeRepository
import uesugi.core.state.meme.MemeService
import uesugi.core.state.memory.MemoryRepository
import uesugi.core.state.memory.MemoryService
import uesugi.core.state.memory.Scopes
import uesugi.core.state.memory.toRecord
import uesugi.core.state.summary.SummaryService

@Serializable
data class FactRequest(
    val keyword: String,
    val description: String,
    val values: String,
    val subjects: String,
    val scopeType: Scopes
)

@Serializable
data class UpdateUserProfileRequest(
    val profile: String,
    val preferences: String
)

@Serializable
data class UpdateMemeRequest(
    val description: String? = null,
    val purpose: String? = null,
    val tags: String? = null
)

@Serializable
data class VocabularyRequest(
    val word: String,
    val type: String,
    val meaning: String,
    val example: String,
    val weight: Int = 50
)

@Serializable
data class UpdateSummaryRequest(
    val timeRange: String,
    val content: String,
    val keyPoints: String,
    val emotionalTone: String? = null,
    val participantCount: Int,
    val messageCount: Int
)

private fun ApplicationCall.botId(): String = parameters["bot-id"]!!
private fun ApplicationCall.groupId(): String = parameters["group-id"]!!
private fun ApplicationCall.userId(): String = parameters["user-id"]!!
private fun ApplicationCall.intPathParam(name: String): Int? = parameters[name]?.toIntOrNull()

private suspend inline fun <reified T : Any> ApplicationCall.receiveOrError(): T? =
    try {
        receive<T>()
    } catch (_: Exception) {
        respond(mapOf("error" to "invalid request body"))
        null
    }

private suspend inline fun <T> ApplicationCall.respondScoped(
    resource: T?,
    botId: String,
    groupId: String,
    getBotId: (T) -> String,
    getGroupId: (T) -> String,
    notFound: String
) {
    if (resource == null || getBotId(resource) != botId || getGroupId(resource) != groupId) {
        respond(mapOf("error" to notFound))
    } else {
        respond(resource)
    }
}

fun Routing.configureBotStatusManager() {
    authenticate("basic") {
        val memoryService by inject<MemoryService>()
        val memoryRepository by inject<MemoryRepository>()
        val memeService by inject<MemeService>()
        val memeRepository by inject<MemeRepository>()
        val evolutionService by inject<EvolutionService>()
        val summaryService by inject<SummaryService>()

        get("/api/bot/{bot-id}/group/{group-id}/facts") {
            call.respond(memoryService.getAllFactsByGroup(call.botId(), call.groupId()).map { it.toRecord() })
        }

        get("/api/bot/{bot-id}/group/{group-id}/facts/{fact-id}") {
            val botId = call.botId()
            val groupId = call.groupId()
            val factId = call.intPathParam("fact-id") ?: return@get call.respond(mapOf("error" to "invalid fact-id"))
            call.respondScoped(
                memoryRepository.getFactById(factId), botId, groupId,
                { it.botMark }, { it.groupId }, "fact not found"
            )
        }

        post("/api/bot/{bot-id}/group/{group-id}/facts") {
            val botId = call.botId()
            val groupId = call.groupId()
            val request = call.receiveOrError<FactRequest>() ?: return@post
            val id = memoryRepository.createFact(
                botMark = botId, groupId = groupId, keyword = request.keyword,
                description = request.description, values = request.values,
                subjects = request.subjects, scopeType = request.scopeType
            )
            val fact = memoryRepository.getFactById(id)
            if (fact == null) {
                call.respond(mapOf("error" to "fact not found"))
            } else {
                call.respond(fact)
            }
        }

        put("/api/bot/{bot-id}/group/{group-id}/facts/{fact-id}") {
            val botId = call.botId()
            val groupId = call.groupId()
            val factId = call.intPathParam("fact-id") ?: return@put call.respond(mapOf("error" to "invalid fact-id"))
            val request = call.receiveOrError<FactRequest>() ?: return@put
            call.respondScoped(
                memoryRepository.updateFact(
                    id = factId, keyword = request.keyword,
                    description = request.description, values = request.values,
                    subjects = request.subjects, scopeType = request.scopeType
                ), botId, groupId,
                { it.botMark }, { it.groupId }, "fact not found"
            )
        }

        delete("/api/bot/{bot-id}/group/{group-id}/facts/{fact-id}") {
            val deleted = memoryService.deleteFact(
                call.botId(),
                call.groupId(),
                call.intPathParam("fact-id") ?: return@delete call.respond(mapOf("error" to "invalid fact-id"))
            )
            call.respond(if (deleted) mapOf("success" to true) else mapOf("error" to "fact not found"))
        }

        get("/api/bot/{bot-id}/group/{group-id}/user-profiles") {
            call.respond(memoryService.getAllUserProfilesByGroup(call.botId(), call.groupId()).map { it.toRecord() })
        }

        get("/api/bot/{bot-id}/group/{group-id}/user-profiles/{user-id}") {
            call.respond(memoryRepository.findOrCreateUserProfile(call.botId(), call.groupId(), call.userId()))
        }

        put("/api/bot/{bot-id}/group/{group-id}/user-profiles/{user-id}") {
            val request = call.receiveOrError<UpdateUserProfileRequest>() ?: return@put
            val profile = memoryRepository.updateUserProfile(
                call.botId(),
                call.groupId(),
                call.userId(),
                request.profile,
                request.preferences
            )
            if (profile == null) {
                call.respond(mapOf("error" to "user-profile not found"))
            } else {
                call.respond(profile)
            }
        }

        delete("/api/bot/{bot-id}/group/{group-id}/user-profiles/{user-id}") {
            val deleted = memoryRepository.deleteUserProfile(call.botId(), call.groupId(), call.userId())
            call.respond(if (deleted) mapOf("success" to true) else mapOf("error" to "user-profile not found"))
        }

        get("/api/bot/{bot-id}/group/{group-id}/memes") {
            call.respond(memeService.getAllMemos(call.botId(), call.groupId()))
        }

        get("/api/bot/{bot-id}/group/{group-id}/memes/{meme-id}") {
            val botId = call.botId()
            val groupId = call.groupId()
            val memeId = call.intPathParam("meme-id") ?: return@get call.respond(mapOf("error" to "invalid meme-id"))
            call.respondScoped(
                memeService.getMemoById(memeId), botId, groupId,
                { it.botId }, { it.groupId }, "meme not found"
            )
        }

        put("/api/bot/{bot-id}/group/{group-id}/memes/{meme-id}") {
            val botId = call.botId()
            val groupId = call.groupId()
            val memeId = call.intPathParam("meme-id") ?: return@put call.respond(mapOf("error" to "invalid meme-id"))
            val request = call.receiveOrError<UpdateMemeRequest>() ?: return@put
            val updated = memeRepository.updateMeme(memeId, request.description, request.purpose, request.tags)
            if (updated != null && updated.botId == botId && updated.groupId == groupId) {
                memeService.upsertToVectorStore(updated)
                call.respond(updated)
            } else {
                call.respond(mapOf("error" to "meme not found"))
            }
        }

        delete("/api/bot/{bot-id}/group/{group-id}/memes/{meme-id}") {
            val deleted = memeService.deleteMemo(
                call.botId(),
                call.groupId(),
                call.intPathParam("meme-id") ?: return@delete call.respond(mapOf("error" to "invalid meme-id"))
            )
            call.respond(if (deleted) mapOf("success" to true) else mapOf("error" to "meme not found"))
        }

        get("/api/bot/{bot-id}/group/{group-id}/vocabulary") {
            call.respond(evolutionService.getAllVocabulary(call.botId(), call.groupId()).map { it.toRecord() })
        }

        get("/api/bot/{bot-id}/group/{group-id}/vocabulary/{vocab-id}") {
            val botId = call.botId()
            val groupId = call.groupId()
            val vocabId = call.intPathParam("vocab-id") ?: return@get call.respond(mapOf("error" to "invalid vocab-id"))
            call.respondScoped(
                evolutionService.getVocabularyById(vocabId), botId, groupId,
                { it.botMark }, { it.groupId }, "vocabulary not found"
            )
        }

        post("/api/bot/{bot-id}/group/{group-id}/vocabulary") {
            val request = call.receiveOrError<VocabularyRequest>() ?: return@post
            val vocab = evolutionService.addOrUpdateWord(
                call.botId(), call.groupId(),
                SlangWord(request.word, request.type, request.meaning, request.example),
                request.weight
            )
            call.respond(vocab)
        }

        put("/api/bot/{bot-id}/group/{group-id}/vocabulary/{vocab-id}") {
            val botId = call.botId()
            val groupId = call.groupId()
            val vocabId = call.intPathParam("vocab-id") ?: return@put call.respond(mapOf("error" to "invalid vocab-id"))
            val request = call.receiveOrError<VocabularyRequest>() ?: return@put
            call.respondScoped(
                evolutionService.updateWordById(
                    vocabId, request.word, request.type, request.meaning, request.example, request.weight
                ), botId, groupId,
                { it.botMark }, { it.groupId }, "vocabulary not found"
            )
        }

        delete("/api/bot/{bot-id}/group/{group-id}/vocabulary/{vocab-id}") {
            val deleted = evolutionService.deleteWordById(
                call.intPathParam("vocab-id") ?: return@delete call.respond(mapOf("error" to "invalid vocab-id")),
                call.botId(), call.groupId()
            )
            call.respond(if (deleted) mapOf("success" to true) else mapOf("error" to "vocabulary not found"))
        }

        get("/api/bot/{bot-id}/group/{group-id}/summaries") {
            call.respond(summaryService.getAllSummariesByGroup(call.botId(), call.groupId()))
        }

        get("/api/bot/{bot-id}/group/{group-id}/summaries/{summary-id}") {
            val botId = call.botId()
            val groupId = call.groupId()
            val summaryId =
                call.intPathParam("summary-id") ?: return@get call.respond(mapOf("error" to "invalid summary-id"))
            call.respondScoped(
                summaryService.getSummaryById(summaryId), botId, groupId,
                { it.botMark }, { it.groupId }, "summary not found"
            )
        }

        put("/api/bot/{bot-id}/group/{group-id}/summaries/{summary-id}") {
            val botId = call.botId()
            val groupId = call.groupId()
            val summaryId =
                call.intPathParam("summary-id") ?: return@put call.respond(mapOf("error" to "invalid summary-id"))
            val request = call.receiveOrError<UpdateSummaryRequest>() ?: return@put
            call.respondScoped(
                summaryService.updateSummary(
                    summaryId, request.timeRange, request.content, request.keyPoints,
                    request.emotionalTone, request.participantCount, request.messageCount
                ), botId, groupId,
                { it.botMark }, { it.groupId }, "summary not found"
            )
        }

        delete("/api/bot/{bot-id}/group/{group-id}/summaries/{summary-id}") {
            val deleted = summaryService.deleteSummary(
                call.intPathParam("summary-id") ?: return@delete call.respond(mapOf("error" to "invalid summary-id"))
            )
            call.respond(if (deleted) mapOf("success" to true) else mapOf("error" to "summary not found"))
        }
    }
}
