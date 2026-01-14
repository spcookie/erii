package uesugi.core.flow

import kotlinx.coroutines.*
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.core.emotion.EmotionChangeEvent
import uesugi.core.emotion.EmotionalTendencies
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import java.util.concurrent.ConcurrentHashMap

data class FlowState(
    var value: Double = 0.0,
    var lastUpdateTime: Long = System.currentTimeMillis()
) {
    fun addCharge(amount: Double, botMark: String, groupId: String) {
        value = (value + amount).coerceIn(0.0, 100.0)
        lastUpdateTime = System.currentTimeMillis()
        EventBus.postAsync(FlowChangeEvent(botMark, groupId, value))
    }

    fun drain(amount: Double, botMark: String, groupId: String) {
        value = (value - amount).coerceIn(0.0, 100.0)
        lastUpdateTime = System.currentTimeMillis()
        EventBus.postAsync(FlowChangeEvent(botMark, groupId, value))
    }
}

class FlowGauge(
    mood: EmotionalTendencies,
    private val botMark: String,
    private val groupId: String,
    private val decayIntervalMs: Long = 1000 * 60L,
    private val persistIntervalMs: Long = 1000 * 120L
) {
    val state = FlowState()
    private val scope = CoroutineScope(Dispatchers.Default)

    var pleasure: Double = mood.pad.normalize().p
    var arousal: Double = mood.pad.normalize().a

    companion object {
        private val log = logger()
    }

    init {
        loadStateFromDB()

        scope.launch {
            while (isActive) {
                delay(decayIntervalMs)
                decayFlow()
            }
        }

        scope.launch {
            while (isActive) {
                delay(persistIntervalMs)
                persistStateToDB()
            }
        }

        subscribe()
    }

    private fun loadStateFromDB() {
        try {
            transaction {
                val flowState = FlowStateEntity.find {
                    (FlowStateTable.botMark eq botMark) and (FlowStateTable.groupId eq groupId)
                }.orderBy(FlowStateTable.lastProcessedAt to SortOrder.DESC).firstOrNull()

                if (flowState != null) {
                    state.value = flowState.flowValue
                    state.lastUpdateTime = flowState.lastUpdateTime
                    log.debug("从数据库加载心流状态, botId=$botMark, groupId=$groupId, value=${state.value}")
                } else {
                    log.debug("群组 botId=$botMark, groupId=$groupId 没有心流状态记录, 使用默认值")
                }
            }
        } catch (e: Exception) {
            log.error("加载心流状态失败, botId=$botMark, groupId=$groupId", e)
        }
    }

    private fun persistStateToDB() {
        try {
            transaction {
                val flowState = FlowStateEntity.find {
                    (FlowStateTable.botMark eq botMark) and (FlowStateTable.groupId eq groupId)
                }.orderBy(FlowStateTable.lastProcessedAt to SortOrder.DESC).firstOrNull()

                if (flowState != null) {
                    flowState.flowValue = state.value
                    flowState.lastUpdateTime = state.lastUpdateTime
                    log.debug("持久化心流状态, botId=$botMark, groupId=$groupId, value=${state.value}")
                }
            }
        } catch (e: Exception) {
            log.error("持久化心流状态失败, botId=$botMark, groupId=$groupId", e)
        }
    }

    private fun subscribe() {
        EventBus.subscribeAsync<FlowEvent>(scope) { event ->
            if (event.botMark != botMark || event.groupId != groupId) {
                return@subscribeAsync
            }

            when (event) {
                is CoreInterestEvent -> this.addEvent(baseCharge = 20.0, interest = event.interest)
                is ContinuousInteractionEvent -> this.addEvent(baseCharge = 10.0, momentum = event.momentum)
                is DeepReplyEvent -> this.addEvent(baseCharge = event.baseCharge)
                is GroupResonanceEvent -> this.addEvent(baseCharge = 10.0, globalArousal = event.globalArousal)

                is NegativeEvent -> this.drainEvent(baseDrain = event.penalty, isNegative = true)
                is TopicInterruptEvent -> this.drainEvent(baseDrain = event.penalty)
                is LowActivityEvent -> this.drainEvent(baseDrain = event.penalty)
                is RepeatTopicEvent -> this.drainEvent(baseDrain = event.penalty)
            }

            log.debug("收到心流事件, botId=$botMark, groupId=$groupId, 当前心流值: ${this.state.value}")
        }

        EventBus.subscribeAsync<EmotionChangeEvent>(scope) { event ->
            if (event.botMark != botMark || event.groupId != groupId) {
                return@subscribeAsync
            }
            val (p, a, _) = event.pad.normalize()
            pleasure = p
            arousal = a

            log.debug("收到情绪变更事件, 当前Emotion: P: ${event.pad.p}, A: ${event.pad.a}")
        }
    }

    fun addEvent(
        baseCharge: Double = 10.0,
        interest: Double = 1.0,
        momentum: Double = 1.0,
        globalArousal: Double = 0.5
    ) {
        val emotionModifier = 1.0 + arousal * 0.5 + pleasure * 0.3
        val gain = baseCharge * interest * momentum * emotionModifier * (1.0 + globalArousal)
        state.addCharge(gain, botMark, groupId)
    }

    fun drainEvent(
        baseDrain: Double = 10.0,
        isNegative: Boolean = false,
        decayFactor: Double = 1.0
    ) {
        var drainAmount = baseDrain * decayFactor
        if (isNegative) {
            drainAmount *= 1.2
            pleasure -= 0.3
        }
        state.drain(drainAmount, botMark, groupId)
    }

    fun decayFlow() {
        val now = System.currentTimeMillis()
        val minutes = (now - state.lastUpdateTime) / 60000.0
        var drainAmount = minutes
        if (pleasure < -0.3) drainAmount = 5.0 * minutes
        state.drain(drainAmount, botMark, groupId)
    }

    fun mapToState(): FlowMeterState {
        return when {
            state.value < 30 -> FlowMeterState.STANDBY
            state.value < 70 -> FlowMeterState.GETTING_BETTER
            else -> FlowMeterState.FLOW_BURST
        }
    }

    fun getFlowMeter(): Double {
        return state.value
    }

    fun stop() {
        persistStateToDB()
        scope.cancel()
    }
}

class FlowGaugeManager {
    private val gauges = ConcurrentHashMap<String, FlowGauge>()

    companion object {
        private val log = logger()
    }

    fun getOrCreate(botMark: String, groupId: String, mood: EmotionalTendencies): FlowGauge {
        val key = "$botMark:$groupId"
        return gauges.getOrPut(key) {
            log.debug("创建新的FlowGauge实例, botId=$botMark, groupId=$groupId")
            FlowGauge(mood, botMark, groupId)
        }
    }

    fun get(botMark: String, groupId: String): FlowGauge? {
        val key = "$botMark:$groupId"
        return gauges[key]
    }

    fun stopAll() {
        gauges.values.forEach { it.stop() }
        gauges.clear()
        log.debug("所有FlowGauge实例已关闭")
    }
}

enum class FlowMeterState {
    STANDBY,
    GETTING_BETTER,
    FLOW_BURST
}