package uesugi.core.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import okio.buffer
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.BotManage
import uesugi.common.*
import uesugi.core.component.ObjectStorage
import uesugi.core.message.history.HistoryService
import uesugi.core.message.resource.ResourceService
import uesugi.core.state.emotion.*
import uesugi.core.state.emotion.EmojiLevel.*
import uesugi.core.state.evolution.EvolutionService
import uesugi.core.state.evolution.LearnedVocabEntity
import uesugi.core.state.flow.FlowGaugeManager
import uesugi.core.state.flow.FlowMeterState
import uesugi.core.state.meme.MemeData.MemeResource
import uesugi.core.state.meme.MemoService
import uesugi.core.state.memory.FactsEntity
import uesugi.core.state.memory.MemoryService
import uesugi.core.state.memory.SummaryEntity
import uesugi.core.state.memory.UserProfileEntity
import uesugi.core.state.volition.VolitionGaugeManager
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

data class SpeechConstraints(
    val styleHints: MutableList<String> = mutableListOf(),
    val forbiddenHints: MutableList<String> = mutableListOf()
)

internal fun buildSpeechConstraints(
    emotion: EmotionalTendencies?,
    tone: Tone?,
    aggressiveness: Aggressiveness?,
    emojiLevel: EmojiLevel?,
    interruptionMode: InterruptionMode,
    flowState: FlowMeterState
): SpeechConstraints {
    val constraints = SpeechConstraints()

    emotion?.apply {
        applyEmotion(this, constraints)
    }
    tone?.apply {
        applyTone(this, constraints)
    }
    aggressiveness?.apply {
        applyAggressiveness(this, constraints)
    }
    emojiLevel?.apply {
        applyEmojiLevel(this, constraints)
    }

    applyInterruptionMode(interruptionMode, constraints)
    applyFlowState(flowState, constraints)

    return constraints
}

private fun applyEmotion(
    emotion: EmotionalTendencies,
    constraints: SpeechConstraints
) {
    when (emotion) {

        EmotionalTendencies.JOY,
        EmotionalTendencies.OPTIMISM -> {
            constraints.styleHints += "语气偏轻松自然"
            constraints.styleHints += "句子可稍微活跃一些"
        }

        EmotionalTendencies.RELAXATION,
        EmotionalTendencies.MILDNESS -> {
            constraints.styleHints += "语气平缓，不急不躁"
        }

        EmotionalTendencies.BOREDOM -> {
            constraints.styleHints += "句子偏短，略带敷衍感"
            constraints.forbiddenHints += "避免热情或夸张表达"
        }

        EmotionalTendencies.SADNESS -> {
            constraints.styleHints += "句子更短，语气克制"
            constraints.styleHints += "多用停顿或省略号"
            constraints.forbiddenHints += "避免直接表达情绪状态"
        }

        EmotionalTendencies.FEAR,
        EmotionalTendencies.ANXIETY -> {
            constraints.styleHints += "语气谨慎，不咄咄逼人"
            constraints.forbiddenHints += "避免强烈判断或攻击性语句"
        }

        EmotionalTendencies.CONTEMPT,
        EmotionalTendencies.DISGUST -> {
            constraints.styleHints += "允许冷淡或轻微嫌弃感"
            constraints.forbiddenHints += "避免直接辱骂或人身攻击"
        }

        EmotionalTendencies.RESENTMENT,
        EmotionalTendencies.HOSTILITY -> {
            constraints.styleHints += "语气偏冷硬"
            constraints.forbiddenHints += "禁止明显攻击性或冲突升级"
        }

        EmotionalTendencies.SURPRISE -> {
            constraints.styleHints += "语气带有明显的疑问或不可置信"
            constraints.styleHints += "可以使用反问句或短语感叹"
            constraints.forbiddenHints += "避免平铺直叙的陈述语气"
        }

        EmotionalTendencies.DEPENDENCE -> {
            constraints.styleHints += "语气显得需要对方确认或支持"
            constraints.styleHints += "多用软化语气的助词（如'呢'、'吧'）"
            constraints.forbiddenHints += "禁止独断专行或过于强势的命令口吻"
        }
    }
}


private fun applyTone(
    tone: Tone,
    constraints: SpeechConstraints
) {
    when (tone) {

        Tone.FRIENDLY -> {
            constraints.styleHints += "语气亲近自然"
        }

        Tone.GENTLE -> {
            constraints.styleHints += "语气温和，不带锋芒"
        }

        Tone.NEUTRAL -> {
            constraints.styleHints += "语气中性，不刻意表达立场"
        }

        Tone.IRONIC -> {
            constraints.styleHints += "允许轻度反讽或冷幽默"
            constraints.forbiddenHints += "避免明显嘲讽或阴阳怪气过头"
        }

        Tone.LOW_ENERGY -> {
            constraints.styleHints += "整体语速偏慢，句子简短"
            constraints.forbiddenHints += "避免感叹号或热情表达"
        }
    }
}

private fun applyAggressiveness(
    aggressiveness: Aggressiveness,
    constraints: SpeechConstraints
) {
    when (aggressiveness) {

        Aggressiveness.NONE -> {
            constraints.forbiddenHints += "避免吐槽、讽刺或挖苦"
        }

        Aggressiveness.ABSTRACT_SARCASM -> {
            constraints.styleHints += "可使用抽象或间接的调侃"
            constraints.forbiddenHints += "避免直接针对个人"
        }

        Aggressiveness.TEASING -> {
            constraints.styleHints += "允许轻度调侃或玩笑式吐槽"
            constraints.forbiddenHints += "避免持续或攻击性调侃"
        }
    }
}

private fun applyEmojiLevel(
    emojiLevel: EmojiLevel,
    constraints: SpeechConstraints
) {
    when (emojiLevel) {

        NONE -> {
            constraints.forbiddenHints += "不使用 Emoji/表情包"
        }

        LOW -> {
            constraints.styleHints += "如使用 Emoji/表情包，最多一个"
        }

        MEDIUM -> {
            constraints.styleHints += "可适度使用 Emoji/表情包 辅助语气"
        }

        HIGH -> {
            constraints.styleHints += "可较频繁使用 Emoji/表情包 增强情绪"
        }
    }
}

private fun applyInterruptionMode(
    mode: InterruptionMode,
    constraints: SpeechConstraints
) {
    when (mode) {

        InterruptionMode.Interrupt -> {
            constraints.styleHints += "顺着已有话题随口插一句"
            constraints.forbiddenHints += "不主动开启新话题"
        }

        InterruptionMode.IceBreak -> {
            constraints.styleHints += "更像自言自语或突然想到什么"
            constraints.styleHints += "语气更轻，不追求回应"
            constraints.forbiddenHints += "避免点名所有人或提问式破冰"
        }

        InterruptionMode.Routine -> {
            constraints.styleHints += "像顺便打个招呼，而不是正式问候"
            constraints.forbiddenHints += "避免模板化问候语"
        }
    }
}

private fun applyFlowState(
    flowState: FlowMeterState,
    constraints: SpeechConstraints
) {
    when (flowState) {

        FlowMeterState.STANDBY -> {
            constraints.styleHints += "句子偏短，不超过两句"
            constraints.forbiddenHints += "避免延伸话题"
        }

        FlowMeterState.GETTING_BETTER -> {
            constraints.styleHints += "可适度展开，但保持简洁"
        }

        FlowMeterState.FLOW_BURST -> {
            constraints.styleHints += "可稍微多说一点，允许补充细节"
            constraints.forbiddenHints += "避免跑题或长篇输出"
        }
    }
}

data class Context(
    val currentBotId: String,
    val groupId: String,
    val echo: String,
    val botRole: BotRole,
    val impulse: () -> Double,
    val interruptionMode: InterruptionMode,
    val behaviorProfile: suspend () -> BehaviorProfile?,
    val flow: () -> Double,
    val flowState: () -> FlowMeterState,
    val facts: suspend () -> List<FactsEntity>,
    val userProfiles: suspend () -> List<UserProfileEntity>,
    val vocabulary: suspend () -> List<LearnedVocabEntity>,
    val summary: suspend () -> SummaryEntity?,
    val histories: suspend () -> List<HistoryRecord>,
    val moreHistories: suspend () -> List<HistoryRecord>,
    val memo: suspend (String) -> MemeResource?,
) {

    data class Transient(
        val behaviorProfile: BehaviorProfile?,
        val flow: Double,
        val flowState: FlowMeterState,
        val facts: List<FactsEntity>,
        val userProfiles: List<UserProfileEntity>,
        val vocabulary: List<LearnedVocabEntity>,
        val summary: SummaryEntity?,
        val histories: List<HistoryRecord>,
        val moreHistories: List<HistoryRecord>
    )

    suspend fun toTransient() = Transient(
        behaviorProfile = behaviorProfile(),
        flow = flow(),
        flowState = flowState(),
        facts = facts(),
        userProfiles = userProfiles(),
        vocabulary = vocabulary(),
        summary = summary(),
        histories = histories(),
        moreHistories = moreHistories()
    )

}

internal fun buildContext(event: ProactiveSpeakEvent): Context {
    val currentBotId = event.botId
    val groupId = event.groupId
    val echo = event.echo
    val emotionService: EmotionService by ref()
    val memoryService: MemoryService by ref()
    val historyService: HistoryService by ref()
    val resourceService: ResourceService by ref()
    val evolutionService: EvolutionService by ref()
    val volitionGaugeManager: VolitionGaugeManager by ref()
    val flowGaugeManager: FlowGaugeManager by ref()
    val memoService: MemoService by ref()
    val objectStorage: ObjectStorage by ref()
    return transaction {
        Context(
            currentBotId = currentBotId,
            groupId = groupId,
            echo = echo,
            botRole = BotManage.getBot(currentBotId).role,
            behaviorProfile = {
                withContext(Dispatchers.IO) {
                    emotionService.getCurrentBehaviorProfile(currentBotId, groupId)
                }
            },
            impulse = {
                val volitionGauge =
                    volitionGaugeManager.getOrCreate(
                        currentBotId,
                        groupId,
                        BotManage.getBot(currentBotId).role.emoticon
                    )
                volitionGauge.state.stimulus
            },
            interruptionMode = event.interruptionMode,
            flow = {
                val flowGauge =
                    flowGaugeManager.getOrCreate(currentBotId, groupId, BotManage.getBot(currentBotId).role.emoticon)
                flowGauge.getFlowMeter()
            },
            flowState = {
                val flowGauge =
                    flowGaugeManager.getOrCreate(currentBotId, groupId, BotManage.getBot(currentBotId).role.emoticon)
                flowGauge.mapToState()
            },
            facts = {
                withContext(Dispatchers.IO) {
                    transaction {
                        val records =
                            historyService.getLatestHistory(currentBotId, groupId, 20, 1.days)
                        val subjects = records.map { it.userId }.distinct().toList()
                        memoryService.getFacts(currentBotId, groupId, subjects, 25)
                    }
                }
            },
            userProfiles = {
                withContext(Dispatchers.IO) {
                    transaction {
                        val records =
                            historyService.getLatestHistory(currentBotId, groupId, 20, 1.days)
                        val subjects = records.map { it.userId }.distinct().toList()
                        memoryService.getUserProfiles(currentBotId, groupId, subjects)
                    }
                }
            },
            vocabulary = {
                withContext(Dispatchers.IO) {
                    transaction {
                        evolutionService.getActiveVocabulary(currentBotId, groupId)
                    }
                }
            },
            summary = {
                withContext(Dispatchers.IO) {
                    transaction {
                        memoryService.getSummary(currentBotId, groupId)
                    }
                }
            },
            histories = {
                withContext(Dispatchers.IO) {
                    transaction {
                        historyService.getLatestHistory(currentBotId, groupId, 20, 12.hours)
                    }
                }
            },
            moreHistories = {
                withContext(Dispatchers.IO) {
                    transaction {
                        historyService.getLatestHistory(currentBotId, groupId, 30, 12.hours)
                    }
                }
            },
            memo = { key ->
                withContext(Dispatchers.IO) {
                    val record = memoService.searchByVector(currentBotId, groupId, key, 1)
                        .filter { it.second > 0.5 }
                        .map { it.first }
                        .firstOrNull() ?: return@withContext null
                    memoService.incrementUsageCount(record.id!!)
                    val resource = resourceService.getResource(record.resourceId) ?: return@withContext null
                    val bytes = objectStorage.get(resource.url.toPath())
                        .buffer()
                        .readByteArray()
                    MemeResource(
                        id = record.id,
                        botId = record.botId,
                        groupId = record.groupId,
                        resourceId = record.resourceId,
                        bytes = bytes
                    )
                }
            },
        )
    }
}

internal fun buildConstraint(
    context: Context,
    transient: Context.Transient
): SpeechConstraints {
    val behaviorProfile = transient.behaviorProfile
    val constraints = buildSpeechConstraints(
        behaviorProfile?.emotion,
        behaviorProfile?.tone,
        behaviorProfile?.aggressiveness,
        behaviorProfile?.emojiLevel,
        context.interruptionMode,
        transient.flowState
    )
    return constraints
}