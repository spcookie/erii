package uesugi.core


sealed interface AgentSendLifeCycleEvent {
    val botId: String
    val groupId: String
    val echo: String
}

data class AgentBeforeSendAndReceiveEvent(
    override val botId: String,
    override val groupId: String,
    override val echo: String,
    val sentences: List<String>
) : AgentSendLifeCycleEvent

data class AgentAfterSendAndReceiveEvent(
    override val botId: String,
    override val groupId: String,
    override val echo: String,
    val sentences: List<String>,
) : AgentSendLifeCycleEvent

data class AgentReceiveReplyEvent(
    override val botId: String,
    override val groupId: String,
    override val echo: String,
    val sentence: String
) : AgentSendLifeCycleEvent

class AgentSendAndReceiveClosedEvent(
    override val botId: String,
    override val groupId: String,
    override val echo: String
) : AgentSendLifeCycleEvent

class AgentSendAndReceiveFinallyEvent(
    override val botId: String,
    override val groupId: String,
    override val echo: String
) : AgentSendLifeCycleEvent

sealed interface AgentDispatchEvent {
    val botId: String
    val groupId: String
    val echo: String
}

class AgentFallbackEvent(
    override val botId: String,
    override val groupId: String,
    override val echo: String,
) : AgentDispatchEvent

class AgentRejectGrabEvent(
    override val botId: String,
    override val groupId: String,
    override val echo: String,
) : AgentDispatchEvent


class AgentCallStartEvent(
    override val botId: String,
    override val groupId: String,
    override val echo: String,
) : AgentDispatchEvent

class AgentCallCompletionEvent(
    val throwable: Throwable?,
    override val botId: String,
    override val groupId: String,
    override val echo: String,
) : AgentDispatchEvent