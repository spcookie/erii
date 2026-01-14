package uesugi.routing

import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject
import uesugi.BotManage
import uesugi.ENABLE_GROUPS
import uesugi.core.emotion.BehaviorProfile
import uesugi.core.emotion.EmotionService
import uesugi.core.evolution.VocabularyService
import uesugi.core.flow.FlowGaugeManager
import uesugi.core.flow.FlowMeterState
import uesugi.core.memory.MemoryService
import uesugi.core.volition.VolitionGaugeManager

fun Routing.configureBotStatus() {
    get("/bots") {
        call.respond(BotManage.getAllBotIds())
    }
    get("/status/{id}") {
        val id = call.request.pathVariables["id"]
        if (id == null) {
            call.respond(mapOf("error" to "id is null"))
        } else {
            val roledBot = BotManage.getBot(id)
            if (roledBot == null) {
                call.respond(mapOf("error" to "bot not exist"))
            } else {
                val groups = roledBot.bot.groups.map { it.id.toString() }.filter { ENABLE_GROUPS.contains(it) }.toList()

                val emotionService by inject<EmotionService>()
                val flowGaugeManager by inject<FlowGaugeManager>()
                val volitionGaugeManager by inject<VolitionGaugeManager>()
                val vocabularyService by inject<VocabularyService>()
                val memoryService by inject<MemoryService>()

                val botStatusByGroups = groups.map { groupId ->
                    val emoticon = BotManage.getBot(id)!!.role.emoticon
                    val behaviorProfile = emotionService.getCurrentBehaviorProfile(id, groupId)
                    val flowState =
                        flowGaugeManager.getOrCreate(id, groupId, emoticon).let { it.state.value to it.mapToState() }
                    val volitionState = volitionGaugeManager.getOrCreate(id, groupId, emoticon)
                        .let { Triple(it.state.stimulus, it.state.fatigue, it.shouldSpeak()) }
                    val vocabularies = vocabularyService.getActiveVocabulary(id, groupId, 999).map { it.word }
                    val summary = memoryService.getSummary(id, groupId)?.content
                    val factSize = memoryService.getFactSize(id, groupId)
                    val userProfileSize = memoryService.getUserProfileSize(id, groupId)

                    BotStatus.ByGroup(
                        groupId = groupId,
                        behaviorProfile = behaviorProfile,
                        flowState = BotStatus.FlowState.fromPair(flowState),
                        volitionState = BotStatus.VolitionState.fromTriple(volitionState),
                        vocabularies = vocabularies,
                        summary = summary,
                        factSize = factSize,
                        userProfileSize = userProfileSize
                    )
                }.toList()

                call.respond(BotStatus(id, groups, botStatusByGroups))
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
        val flowState: FlowState,
        val volitionState: VolitionState,
        val vocabularies: List<String>,
        val summary: String?,
        val factSize: Long,
        val userProfileSize: Long
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