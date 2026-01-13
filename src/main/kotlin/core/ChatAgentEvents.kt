package uesugi.core


sealed interface AgentSendLifeCycleEvent {
    val botId: String
    val groupId: String
}

data class AgentBeforeSendAndReceiveEvent(
    override val botId: String,
    override val groupId: String,
    val sentences: List<String>
) : AgentSendLifeCycleEvent

data class AgentAfterSendAndReceiveEvent(
    override val botId: String,
    override val groupId: String,
    val sentences: List<String>
) : AgentSendLifeCycleEvent

data class AgentReceiveReplyEvent(
    override val botId: String,
    override val groupId: String,
    val sentence: String
) : AgentSendLifeCycleEvent

class AgentSendAndReceiveClosedEvent(
    override val botId: String,
    override val groupId: String
) : AgentSendLifeCycleEvent

class AgentSendAndReceiveFinallyEvent(
    override val botId: String,
    override val groupId: String
) : AgentSendLifeCycleEvent

sealed interface AgentDispatchEvent {
    val botId: String
    val groupId: String
    val speak: ProactiveSpeakEvent
}

class AgentFallbackEvent(
    override val botId: String,
    override val groupId: String,
    override val speak: ProactiveSpeakEvent
) : AgentDispatchEvent

class AgentRejectGrabEvent(
    override val botId: String,
    override val groupId: String,
    override val speak: ProactiveSpeakEvent
) : AgentDispatchEvent
