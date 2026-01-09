package uesugi.core.volition

import kotlinx.coroutines.*
import uesugi.core.emotion.EmotionChangeEvent
import uesugi.core.emotion.EmotionalTendencies
import uesugi.core.flow.FlowChangeEvent
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger

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

    fun decayFatigue(amount: Double) {
        fatigue = (fatigue - amount).coerceIn(0.0, 100.0)
    }
}

class VolitionGauge(
    private var mood: EmotionalTendencies,
    private val baseDesire: Double = 15.0,
    private val decayIntervalMs: Long = 60000L
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
        scope.launch {
            while (isActive) {
                delay(decayIntervalMs)
                decayFatigue()
            }
        }
        subscribe()
    }

    fun getMood() = mood

    private fun subscribe() {
        EventBus.subscribeAsync<VolitionEvent>(scope) { event ->
            when (event) {
                is ResetStimulusEvent -> resetStimulus(event.stimulus)
                is KeywordHitEvent -> if (arousal > 0.3) addStimulus(event.stimulus)
                is BusyGroupEvent -> addStimulus(event.stimulus)
                is IndirectMentionEvent -> addStimulus(event.stimulus)
                is EmotionalResonanceEvent -> addStimulus(event.stimulus)
            }
        }

        EventBus.subscribeAsync<EmotionChangeEvent>(scope) { event ->
            mood = EmotionalTendencies.findClosest(event.pad)
            val (p, a, _) = event.pad.normalize()
            pleasure = p
            arousal = a
            log.info("Volition收到情绪变更事件, P: $p , A: $a")
        }

        EventBus.subscribeAsync<FlowChangeEvent>(scope) { event ->
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

    fun resetStimulus(amount: Double) {
        state.stimulus = amount
        log.info("刺激值重置: $amount")
    }

    fun addStimulus(amount: Double) {
        state.addStimulus(amount)
        log.info("刺激值增加: +$amount, 当前刺激值: ${state.stimulus}")
    }

    fun addFatigue(amount: Double) {
        state.addFatigue(amount)
        log.info("疲劳值增加: +$amount, 当前疲劳值: ${state.fatigue}")
    }

    fun decayFatigue() {
        val decayRate = if (arousal < 0.2) 8.0 else 5.0
        state.decayFatigue(decayRate)
    }

    fun shouldSpeak(): Boolean {
        val impulse = calculateImpulse()

        val threshold = if (flowValue > 70) 60.0 else 80.0

        return impulse > threshold
    }

    fun stop() {
        scope.cancel()
    }
}
