package uesugi.core.agent

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import uesugi.core.ProactiveSpeakEvent


sealed interface AgentToolCallEvent {
    val botId: String
    val groupId: String
    val echo: String
    val toolName: String
    val toolArgs: JsonObject
}

data class AgentToolCallStartEvent(
    override val botId: String,
    override val groupId: String,
    override val echo: String,
    override val toolName: String,
    override val toolArgs: JsonObject
) : AgentToolCallEvent

data class AgentToolCallCompleteEvent(
    override val botId: String,
    override val groupId: String,
    override val echo: String,
    override val toolName: String,
    override val toolArgs: JsonObject,
    val toolResult: JsonElement?,
    val toolError: String?
) : AgentToolCallEvent

sealed interface AgentDispatchEvent {
    val botId: String
    val groupId: String
    val echo: String
}

class AgentRunStartEvent(
    override val botId: String,
    override val groupId: String,
    override val echo: String,
) : AgentDispatchEvent

class AgentRunCompleteEvent(
    val throwable: Throwable?,
    override val botId: String,
    override val groupId: String,
    override val echo: String,
) : AgentDispatchEvent

class AgentCallFallbackEvent(
    override val botId: String,
    override val groupId: String,
    override val echo: String,
) : AgentDispatchEvent

class AgentCallRejectEvent(
    override val botId: String,
    override val groupId: String,
    override val echo: String,
) : AgentDispatchEvent


class AgentCallStartEvent(
    override val botId: String,
    override val groupId: String,
    override val echo: String,
) : AgentDispatchEvent

class AgentCallCompleteEvent(
    val throwable: Throwable?,
    override val botId: String,
    override val groupId: String,
    override val echo: String,
) : AgentDispatchEvent

data class ChatUrgentEvent(val urgent: ProactiveSpeakEvent)