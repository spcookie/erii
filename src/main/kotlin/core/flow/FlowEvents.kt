package uesugi.core.flow

sealed class FlowEvent {
    abstract val botMark: String
    abstract val groupId: String
}

data class CoreInterestEvent(
    override val botMark: String,
    override val groupId: String,
    val interest: Double = 1.0
) : FlowEvent()

data class ContinuousInteractionEvent(
    override val botMark: String,
    override val groupId: String,
    val momentum: Double = 1.0
) : FlowEvent()

data class DeepReplyEvent(
    override val botMark: String,
    override val groupId: String,
    val baseCharge: Double = 5.0
) : FlowEvent()

data class GroupResonanceEvent(
    override val botMark: String,
    override val groupId: String,
    val globalArousal: Double = 0.5
) : FlowEvent()

data class NegativeEvent(
    override val botMark: String,
    override val groupId: String,
    val penalty: Double = 40.0
) : FlowEvent()

data class TopicInterruptEvent(
    override val botMark: String,
    override val groupId: String,
    val penalty: Double = 30.0,
    val matchScore: Double = 0.0
) : FlowEvent()

data class LowActivityEvent(
    override val botMark: String,
    override val groupId: String,
    val penalty: Double = 5.0
) : FlowEvent()

data class RepeatTopicEvent(
    override val botMark: String,
    override val groupId: String,
    val penalty: Double = 10.0
) : FlowEvent()

data class FlowChangeEvent(
    val botMark: String,
    val groupId: String,
    val value: Double = 0.0
)