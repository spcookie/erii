package uesugi.core.volition

sealed class VolitionEvent


data class ResetStimulusEvent(val stimulus: Double = 0.0) : VolitionEvent()
data class KeywordHitEvent(val stimulus: Double = 30.0) : VolitionEvent()
data class BusyGroupEvent(val stimulus: Double = 10.0) : VolitionEvent()
data class IndirectMentionEvent(val stimulus: Double = 25.0) : VolitionEvent()
data class EmotionalResonanceEvent(val stimulus: Double = 15.0) : VolitionEvent()

data class ProactiveSpeakEvent(val impulse: Double, val interruptionMode: InterruptionMode)

enum class InterruptionMode(val value: String) {
    Interrupt("插话"),
    Icebreak("打破安静发言"),
    Routine("定时发言")
}