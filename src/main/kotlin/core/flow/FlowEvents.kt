package uesugi.core.flow

// ------------------------ 心流事件类 ------------------------
sealed class FlowEvent

// 增量事件
data class CoreInterestEvent(val interest: Double = 1.0) : FlowEvent()
data class ContinuousInteractionEvent(val momentum: Double = 1.0) : FlowEvent()
data class DeepReplyEvent(val baseCharge: Double = 5.0) : FlowEvent()
data class GroupResonanceEvent(val globalArousal: Double = 0.5) : FlowEvent()

// 消耗事件
data class NegativeEvent(val penalty: Double = 40.0) : FlowEvent()
data class TopicInterruptEvent(val penalty: Double = 30.0, val matchScore: Double = 0.0) : FlowEvent()
data class LowActivityEvent(val penalty: Double = 5.0) : FlowEvent()
data class RepeatTopicEvent(val penalty: Double = 10.0) : FlowEvent()

data class FlowChangeEvent(val value: Double = 0.0)