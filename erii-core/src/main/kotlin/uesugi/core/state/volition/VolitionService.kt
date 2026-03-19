package uesugi.core.state.volition

import kotlinx.coroutines.*
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.BotManage
import uesugi.common.*
import uesugi.common.PSFeature.CHAT_URGENT
import uesugi.common.PSFeature.GRAB
import uesugi.common.PSFeature.IGNORE_INTERRUPT
import uesugi.core.plugin.MetaImpl
import uesugi.core.route.MetaToolSetRegister
import uesugi.core.state.emotion.EmotionChangeEvent
import uesugi.core.state.flow.FlowChangeEvent
import uesugi.spi.MetaToolSet.Companion.meta
import java.util.concurrent.ConcurrentHashMap
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class VolitionState(
    var fatigue: Double = 0.0,
    var stimulus: Double = 0.0,
    var lastActiveTime: Long = System.currentTimeMillis()
) {
    fun addFatigue(amount: Double) {
        fatigue = (fatigue + amount).coerceIn(0.0, 100.0)
    }

    fun addStimulus(amount: Double) {
        stimulus = (stimulus + amount).coerceIn(0.0, 100.0)
    }

    fun minusStimulus(amount: Double) {
        stimulus = (stimulus - amount).coerceIn(0.0, 100.0)
    }

    fun decayFatigue(amount: Double) {
        fatigue = (fatigue - amount).coerceIn(0.0, 100.0)
    }
}

class VolitionGauge(
    private var mood: EmotionalTendencies,
    private val botMark: String,
    private val groupId: String,
    private val baseDesire: Double = 15.0,
    private val decayIntervalMs: Long = 1000 * 60L,
    private val persistIntervalMs: Long = 1000 * 60L
) {
    val state = VolitionState()
    private val scope = CoroutineScope(Dispatchers.Default)

    private var pleasure: Double = mood.pad.normalize().p
    private var arousal: Double = mood.pad.normalize().a
    private var flowValue: Double = 0.0

    companion object {
        private val log = logger()
    }

    init {
        loadStateFromDB()

        scope.launch {
            while (isActive) {
                delay(decayIntervalMs)
                decayFatigue()
            }
        }

        scope.launch {
            while (isActive) {
                delay(persistIntervalMs)
                persistStateToDB()
            }
        }

        scope.launch {
            while (isActive) {
                delay(decayIntervalMs)
                val gauge = this@VolitionGauge
                if (gauge.shouldSpeak()) {
                    log.info("决策: 机器人 $botMark 群组 $groupId 应该主动发言!")

                    EventBus.postAsync(
                        ProactiveSpeakEvent(
                            botId = botMark,
                            _groupId = groupId,
                            interruptionMode = InterruptionMode.Interrupt,
                        )
                    )

                    gauge.addFatigue(100.0)
                    gauge.state.lastActiveTime = System.currentTimeMillis()
                }
            }
        }

        subscribe()
    }

    fun getMood() = mood

    private fun loadStateFromDB() {
        try {
            transaction {
                val volitionState = VolitionStateEntity.find {
                    (VolitionStateTable.botMark eq botMark) and (VolitionStateTable.groupId eq groupId)
                }.orderBy(VolitionStateTable.lastProcessedAt to SortOrder.DESC).firstOrNull()

                if (volitionState != null) {
                    state.fatigue = volitionState.fatigue
                    state.stimulus = volitionState.stimulus
                    state.lastActiveTime = volitionState.lastActiveTime
                    log.debug("从数据库加载主动意愿状态, botId=$botMark, groupId=$groupId, fatigue=${state.fatigue}, stimulus=${state.stimulus}")
                } else {
                    log.debug("群组 botId=$botMark, groupId=$groupId 没有主动意愿状态记录, 使用默认值")
                }
            }
        } catch (e: Exception) {
            log.error("加载主动意愿状态失败, botId=$botMark, groupId=$groupId", e)
        }
    }

    private fun persistStateToDB() {
        try {
            transaction {
                val volitionState = VolitionStateEntity.find {
                    (VolitionStateTable.botMark eq botMark) and (VolitionStateTable.groupId eq groupId)
                }.orderBy(VolitionStateTable.lastProcessedAt to SortOrder.DESC).firstOrNull()

                if (volitionState != null) {
                    volitionState.fatigue = state.fatigue
                    volitionState.stimulus = state.stimulus
                    volitionState.lastActiveTime = state.lastActiveTime
                    log.debug("持久化主动意愿状态, botId=$botMark, groupId=$groupId, fatigue=${state.fatigue}, stimulus=${state.stimulus}")
                }
            }
        } catch (e: Exception) {
            log.error("持久化主动意愿状态失败, botId=$botMark, groupId=$groupId", e)
        }
    }

    private fun subscribe() {
        EventBus.subscribeAsync<VolitionEvent>(scope) { event ->
            if (event.botMark != botMark || event.groupId != groupId) {
                return@subscribeAsync
            }

            when (event) {
                is ResetStimulusEvent -> minusStimulus(event.stimulus)
                is KeywordHitEvent -> if (arousal > 0.3) addStimulus(event.stimulus)
                is BusyGroupEvent -> addStimulus(event.stimulus)
                is IndirectMentionEvent -> addStimulus(event.stimulus)
                is EmotionalResonanceEvent -> addStimulus(event.stimulus)
            }
        }

        EventBus.subscribeAsync<EmotionChangeEvent>(scope) { event ->
            if (event.botMark != botMark || event.groupId != groupId) {
                return@subscribeAsync
            }
            mood = EmotionalTendencies.findClosest(event.pad)
            val (p, a, _) = event.pad.normalize()
            pleasure = p
            arousal = a
            log.debug("Volition收到情绪变更事件, botId=$botMark, groupId=$groupId, P: $p , A: $a")
        }

        EventBus.subscribeAsync<FlowChangeEvent>(scope) { event ->
            if (event.botMark != botMark || event.groupId != groupId) {
                return@subscribeAsync
            }
            flowValue = event.value
        }
    }

    fun calculateImpulse(): Double {
        val emotionModifier = arousal * 30 - maxOf(0.0, -pleasure * 20)
        val flowBonus = if (flowValue > 70) (flowValue - 70) * 1.0 else 0.0

        val stimulus = state.stimulus
        val impulse = (baseDesire + stimulus + emotionModifier + flowBonus) - state.fatigue

        return impulse.coerceIn(0.0, 100.0)
    }

    fun minusStimulus(amount: Double) {
        state.minusStimulus(amount)
        log.debug("刺激值重置: $amount, botId=$botMark, groupId=$groupId")
    }

    fun addStimulus(amount: Double) {
        state.addStimulus(amount)
        log.debug("刺激值增加: +$amount, 当前刺激值: ${state.stimulus}, botId=$botMark, groupId=$groupId")
    }

    fun addFatigue(amount: Double) {
        state.addFatigue(amount)
        log.debug("疲劳值增加: +$amount, 当前疲劳值: ${state.fatigue}, botId=$botMark, groupId=$groupId")
    }

    fun decayFatigue() {
        val decayRate = if (arousal < 0.2) 2.0 else 1.0
        state.decayFatigue(decayRate)
    }

    fun shouldSpeak(): Boolean {
        val impulse = calculateImpulse()

        val threshold = if (flowValue > 70) 70.0 else 95.0

        return impulse > threshold
    }

    fun stop() {
        persistStateToDB()
        scope.cancel()
    }
}

class VolitionGaugeManager {
    private val gauges = ConcurrentHashMap<String, VolitionGauge>()

    companion object {
        private val log = logger()
    }

    fun getOrCreate(botMark: String, groupId: String, mood: EmotionalTendencies): VolitionGauge {
        val key = "$botMark:$groupId"
        return gauges.getOrPut(key) {
            log.debug("创建新的VolitionGauge实例, botId=$botMark, groupId=$groupId")
            VolitionGauge(mood, botMark, groupId)
        }
    }

    fun get(botMark: String, groupId: String): VolitionGauge? {
        val key = "$botMark:$groupId"
        return gauges[key]
    }

    fun getAllGauges(): Map<String, VolitionGauge> {
        return gauges.toMap()
    }

    fun stopAll() {
        gauges.values.forEach { it.stop() }
        gauges.clear()
        log.debug("所有VolitionGauge实例已关闭")
    }
}

@OptIn(ExperimentalUuidApi::class)
fun speakV(
    botId: String,
    groupId: String,
    senderId: String? = null,
    input: String? = null,
    echo: String? = null,
    interruptionMode: InterruptionMode = InterruptionMode.Interrupt,
) {
    val metaToolSets = MetaToolSetRegister.getAllToolSets()
        .map { toolSetApply ->
            toolSetApply().apply {
                meta = MetaImpl(
                    botId = botId,
                    groupId = groupId,
                    senderId = senderId,
                    roledBot = BotManage.getBot(botId),
                    input = input,
                    echo = echo
                )
            }
        }
    val event = ProactiveSpeakEvent(
        botId = botId,
        _groupId = groupId,
        senderId = senderId,
        webSearch = true,
        interruptionMode = interruptionMode,
        feature = CHAT_URGENT or GRAB or IGNORE_INTERRUPT,
        input = input,
        echo = echo ?: Uuid.random().toHexString(),
        toolSetBuilder = { metaToolSets }
    )
    EventBus.postAsync(event)
}
