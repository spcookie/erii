package uesugi.routing

import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import uesugi.BotManage
import uesugi.ENABLE_GROUPS
import uesugi.core.state.emotion.BehaviorProfile
import uesugi.core.state.emotion.EmotionService
import uesugi.core.state.emotion.PAD
import uesugi.core.state.evolution.VocabularyService
import uesugi.core.state.flow.FlowGaugeManager
import uesugi.core.state.flow.FlowMeterState
import uesugi.core.state.memory.MemoryService
import uesugi.core.state.memory.Scopes
import uesugi.core.state.volition.VolitionGaugeManager

fun Routing.configureBotStatus() {
    authenticate("basic") {
        get("/bots") {
            call.respond(BotManage.getAllBotIds())
        }
        get("/status/{id}") {
            val id = call.request.pathVariables["id"]
            if (id == null) {
                call.respond(mapOf("error" to "id is null"))
            } else {
                val roledBot = BotManage.getBot(id)
                val groups =
                    roledBot.refBot.groups.map { it.id.toString() }.filter { ENABLE_GROUPS.contains(it) }.toList()

                val emotionService by inject<EmotionService>()
                val flowGaugeManager by inject<FlowGaugeManager>()
                val volitionGaugeManager by inject<VolitionGaugeManager>()
                val vocabularyService by inject<VocabularyService>()
                val memoryService by inject<MemoryService>()

                val botStatusByGroups = groups.map { groupId ->
                    val emoticon = BotManage.getBot(id).role.emoticon
                    val behaviorProfile = emotionService.getCurrentBehaviorProfile(id, groupId)
                    val pad = emotionService.getCurrentMood(id, groupId)
                    val flowState =
                        flowGaugeManager.getOrCreate(id, groupId, emoticon).let { it.state.value to it.mapToState() }
                    val volitionState = volitionGaugeManager.getOrCreate(id, groupId, emoticon)
                        .let { Triple(it.state.stimulus, it.state.fatigue, it.shouldSpeak()) }
                    val vocabularies = vocabularyService.getActiveVocabulary(id, groupId, 999).map { it.word }
                    val summary = memoryService.getSummary(id, groupId)?.content
                    val factSize = memoryService.getFactSize(id, groupId)
                    val userProfileSize = memoryService.getUserProfileSize(id, groupId)
                    val allFactsByGroup = memoryService.getAllFactsByGroup(id, groupId)
                    val allUserProfilesByGroup = memoryService.getAllUserProfilesByGroup(id, groupId)

                    val scopeByFacts = allFactsByGroup.groupBy(
                        { it.scopeType },
                        {
                            BotStatus.Fact(
                                it.keyword,
                                it.description,
                                it.values,
                                it.subjects.split(",")
                            )
                        }
                    )

                    val facts = BotStatus.Facts(
                        group = scopeByFacts[Scopes.GROUP] ?: emptyList(),
                        user = scopeByFacts[Scopes.USER] ?: emptyList()
                    )

                    val userProfiles = allUserProfilesByGroup.map {
                        BotStatus.UserProfile(
                            id = it.userId,
                            profile = it.profile,
                            preferences = it.preferences
                        )
                    }

                    BotStatus.ByGroup(
                        groupId = groupId,
                        behaviorProfile = behaviorProfile,
                        pad = pad,
                        flowState = BotStatus.FlowState.fromPair(flowState),
                        volitionState = BotStatus.VolitionState.fromTriple(volitionState),
                        vocabularies = vocabularies,
                        summary = summary,
                        factSize = factSize,
                        userProfileSize = userProfileSize,
                        facts = facts,
                        userProfiles = userProfiles
                    )
                }.toList()

                call.respond(BotStatus(id, groups, botStatusByGroups))
            }
        }

        // 新增：单群组状态页面路由
        get("/view/{botId}/{groupId}") {
            val botId = call.parameters["botId"]
            val groupId = call.parameters["groupId"]

            if (botId == null || groupId == null) {
                call.respondRedirect("/")
            } else {
                call.respondText(
                    this::class.java.classLoader.getResource("public/group-status.html")!!.readText(),
                    io.ktor.http.ContentType.Text.Html
                )
            }
        }
    }
}

@Serializable
data class BotStatus(
    val id: String, val groups: List<String>, val status: List<ByGroup>
) {
    @Serializable
    data class ByGroup(
        val groupId: String,
        val behaviorProfile: BehaviorProfile?,
        val pad: PAD?,
        val flowState: FlowState,
        val volitionState: VolitionState,
        val vocabularies: List<String>,
        val summary: String?,
        val factSize: Long,
        val userProfileSize: Long,
        val facts: Facts,
        val userProfiles: List<UserProfile>,
    )

    @Serializable
    data class Facts(
        val group: List<Fact>,
        val user: List<Fact>
    )

    @Serializable
    data class Fact(
        val keyword: String,
        val description: String,
        val values: String,
        val subjects: List<String>
    )

    @Serializable
    data class UserProfile(
        var id: String,
        var profile: String,
        var preferences: String
    )

    @Serializable
    data class VolitionState(val stimulus: Double, val fatigue: Double, val shouldSpeak: Boolean) {
        companion object {
            fun fromTriple(triple: Triple<Double, Double, Boolean>): VolitionState {
                return VolitionState(triple.first, triple.second, triple.third)
            }
        }
    }

    @Serializable
    data class FlowState(val meter: Double, val state: FlowMeterState) {
        companion object {
            fun fromPair(pair: Pair<Double, FlowMeterState>): FlowState {
                return FlowState(pair.first, pair.second)
            }
        }
    }
}