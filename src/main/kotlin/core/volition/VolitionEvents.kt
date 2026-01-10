package uesugi.core.volition

import uesugi.DEBUG_GROUP_ID

sealed class VolitionEvent {
    abstract val botMark: String
    abstract val groupId: String
}

data class ResetStimulusEvent(
    override val botMark: String,
    override val groupId: String,
    val stimulus: Double = 0.0
) : VolitionEvent()

data class KeywordHitEvent(
    override val botMark: String,
    override val groupId: String,
    val stimulus: Double = 30.0
) : VolitionEvent()

data class BusyGroupEvent(
    override val botMark: String,
    override val groupId: String,
    val stimulus: Double = 10.0
) : VolitionEvent()

data class IndirectMentionEvent(
    override val botMark: String,
    override val groupId: String,
    val stimulus: Double = 25.0
) : VolitionEvent()

data class EmotionalResonanceEvent(
    override val botMark: String,
    override val groupId: String,
    val stimulus: Double = 15.0
) : VolitionEvent()

data class ProactiveSpeakEvent(
    val botMark: String,
    val groupId: String,
    val impulse: Double,
    val interruptionMode: InterruptionMode,
    val debugGroupId: String? = DEBUG_GROUP_ID
)

enum class InterruptionMode(val value: String) {
    Interrupt("插话"),
    Icebreak("打破安静发言"),
    Routine("定时发言")
}
