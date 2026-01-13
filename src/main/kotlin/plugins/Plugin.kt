package plugins

import ai.koog.agents.core.tools.reflect.ToolSet
import net.mamoe.mirai.contact.Group
import uesugi.BotManage
import uesugi.DEBUG_GROUP_ID
import uesugi.core.*
import uesugi.toolkit.EventBus

interface Plugin {
    fun onLoad()
    fun onUnload()
}

interface SendAgentState {
    fun init(group: Group) {}
    fun before(sentences: List<String>, group: Group) {}
    fun after(sentences: List<String>, group: Group) {}
    fun replay(sentence: String, group: Group) {}
    fun closed(group: Group) {}
    fun finally(group: Group) {}
    fun reject(event: ProactiveSpeakEvent, group: Group, close: () -> Unit) {}
    fun fallback(event: ProactiveSpeakEvent, group: Group, close: () -> Unit) {}
}

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    state: SendAgentState
) = sendAgent(botId, groupId, input, null, state)

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    chatPointRule: String? = null,
    state: SendAgentState
) = sendAgent(botId, groupId, input, chatPointRule, null, state)

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    chatPointRule: String? = null,
    toolSets: ToolSet? = null,
    state: SendAgentState
) {
    val roledBot = BotManage.getBot(botId) ?: return
    val group = roledBot.bot.getGroup(DEBUG_GROUP_ID?.toLong() ?: groupId.toLong()) ?: return

    state.init(group)

    val lifeCycleRef = mutableListOf<(AgentSendLifeCycleEvent) -> Unit>()
    val lifeCycleSubscriber: (AgentSendLifeCycleEvent) -> Unit = { event ->
        val lifeCycleEvent = event.takeIf { event.botId == botId && event.groupId == groupId }
        if (lifeCycleEvent != null) {
            when (lifeCycleEvent) {
                is AgentBeforeSendAndReceiveEvent -> state.before(lifeCycleEvent.sentences, group)
                is AgentAfterSendAndReceiveEvent -> state.after(lifeCycleEvent.sentences, group)
                is AgentReceiveReplyEvent -> state.replay(lifeCycleEvent.sentence, group)
                is AgentSendAndReceiveClosedEvent -> state.closed(group)
                is AgentSendAndReceiveFinallyEvent -> {
                    try {
                        state.finally(group)
                    } finally {
                        lifeCycleRef.forEach { EventBus.unsubscribeSync<AgentSendLifeCycleEvent>(it) }
                    }
                }
            }
        }
    }
    lifeCycleRef += lifeCycleSubscriber
    EventBus.subscribeSync<AgentSendLifeCycleEvent>(lifeCycleSubscriber)

    val dispatchRef = mutableListOf<(AgentDispatchEvent) -> Unit>()
    val dispatchRefClose = {
        dispatchRef.forEach { EventBus.unsubscribeSync<AgentDispatchEvent>(it) }
    }
    val dispatchSubscriber: (AgentDispatchEvent) -> Unit = { event ->
        val dispatchEvent = event.takeIf { event.botId == botId && event.groupId == groupId }
        if (dispatchEvent != null) {
            when (dispatchEvent) {
                is AgentRejectGrabEvent -> {
                    state.reject(dispatchEvent.speak, group, dispatchRefClose)
                }

                is AgentFallbackEvent -> {
                    state.fallback(dispatchEvent.speak, group, dispatchRefClose)
                }
            }
        }
    }
    dispatchRef += dispatchSubscriber
    EventBus.subscribeSync<AgentDispatchEvent>(dispatchSubscriber)

    EventBus.postAsync(
        ProactiveSpeakEvent(
            botId,
            groupId,
            0.0,
            InterruptionMode.Interrupt,
            input,
            chatPointRule,
            toolSets,
        )
    )


}