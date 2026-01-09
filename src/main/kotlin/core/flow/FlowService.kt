package uesugi.core.flow

import kotlinx.coroutines.*
import uesugi.core.emotion.EmotionChangeEvent
import uesugi.core.emotion.EmotionalTendencies
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger

// ------------------------ 心流状态 ------------------------
data class FlowState(
    var value: Double = 0.0,
    var lastUpdateTime: Long = System.currentTimeMillis()
) {
    fun addCharge(amount: Double) {
        value = (value + amount).coerceIn(0.0, 100.0)
        lastUpdateTime = System.currentTimeMillis()
        EventBus.postAsync(FlowChangeEvent(value))
    }

    fun drain(amount: Double) {
        value = (value - amount).coerceIn(0.0, 100.0)
        lastUpdateTime = System.currentTimeMillis()
        EventBus.postAsync(FlowChangeEvent(value))
    }
}

// ------------------------ 心流槽管理器 ------------------------
class FlowGauge(
    mood: EmotionalTendencies,
    private val decayIntervalMs: Long = 1000L
) {
    val state = FlowState()
    private val scope = CoroutineScope(Dispatchers.Default)

    // 心流衰减参数
    var pleasure: Double = mood.pad.normalize().p
    var arousal: Double = mood.pad.normalize().a

    companion object {
        private val log = logger()
    }

    init {
        // 自动衰减协程
        scope.launch {
            while (isActive) {
                delay(decayIntervalMs)
                decayFlow()
            }
        }
        // 异步订阅事件
        subscribe()
    }

    private fun subscribe() {
        EventBus.subscribeAsync<FlowEvent>(scope) { event ->
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

            log.info("收到心流事件, 当前心流值: ${this.state.value}")
        }

        EventBus.subscribeAsync<EmotionChangeEvent>(scope) { event ->
            val (p, a, _) = event.pad.normalize()
            pleasure = p
            arousal = a

            log.info("收到情绪变更事件, 当前Emotion: P: ${event.pad.p}, A: ${event.pad.a}")
        }
    }

    // ------------------------ 增量事件 ------------------------
    fun addEvent(
        baseCharge: Double = 10.0,
        interest: Double = 1.0,
        momentum: Double = 1.0,
        globalArousal: Double = 0.5
    ) {
        val emotionModifier = 1.0 + arousal * 0.5 + pleasure * 0.3
        val gain = baseCharge * interest * momentum * emotionModifier * (1.0 + globalArousal)
        state.addCharge(gain)
    }

    // ------------------------ 消耗事件 ------------------------
    fun drainEvent(
        baseDrain: Double = 10.0,
        isNegative: Boolean = false,
        decayFactor: Double = 1.0
    ) {
        var drainAmount = baseDrain * decayFactor
        if (isNegative) {
            drainAmount *= 1.2 // 负面事件加速衰减
            pleasure -= 0.3    // 联动情绪
        }
        state.drain(drainAmount)
    }

    // ------------------------ 时间衰减 ------------------------
    fun decayFlow() {
        val now = System.currentTimeMillis()
        val minutes = (now - state.lastUpdateTime) / 60000.0
        var drainAmount = 10.0 * minutes
        if (pleasure < -0.3) drainAmount = 15.0 * minutes
        state.drain(drainAmount)
    }

    // ------------------------ LLM参数映射 ------------------------
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
        scope.cancel()
    }
}

enum class FlowMeterState(val value: String) {
    STANDBY("短回复"),
    GETTING_BETTER("中等回复"),
    FLOW_BURST("长回复")
}