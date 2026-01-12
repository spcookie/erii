package uesugi.core.volition


sealed class VolitionEvent {
    abstract val botMark: String
    abstract val groupId: String
}

data class ResetStimulusEvent(
    override val botMark: String,
    override val groupId: String,
    val stimulus: Double = 50.0
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
