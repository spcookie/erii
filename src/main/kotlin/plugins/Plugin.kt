package plugins

import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.coroutines.CoroutineScope
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
    val scope: CoroutineScope
    fun init(group: Group) {}
    fun before(sentences: List<String>, group: Group) {}
    fun after(sentences: List<String>, group: Group) {}
    fun replay(sentence: String, group: Group) {}
    fun closed(group: Group) {}
    fun finally(group: Group) {}
    fun reject(event: ProactiveSpeakEvent, group: Group) {}
    fun fallback(event: ProactiveSpeakEvent, group: Group) {}
    fun callStart(event: AgentCallStartEvent, group: Group) {}
    fun callCompletion(event: AgentCallCompletionEvent, group: Group) {}
}

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    state: SendAgentState
) = sendAgent(botId, groupId, input, null, null, state)

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    flag: ProactiveSpeakFeatureFlag,
    state: SendAgentState
) = sendAgent(botId, groupId, input, null, null, flag, state)


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
    toolSets: ((ChatToolSet) -> ToolSet)? = null,
    state: SendAgentState
) = sendAgent(botId, groupId, input, null, toolSets, state)

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    chatPointRule: String? = null,
    toolSets: ((ChatToolSet) -> ToolSet)? = null,
    state: SendAgentState
) = sendAgent(botId, groupId, input, chatPointRule, toolSets, ProactiveSpeakFeature.NONE, state)

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    chatPointRule: String? = null,
    toolSets: ((ChatToolSet) -> ToolSet)? = null,
    flag: ProactiveSpeakFeatureFlag,
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
    val dispatchSubscriber: (AgentDispatchEvent) -> Unit = { event ->
        val dispatchEvent = event.takeIf { event.botId == botId && event.groupId == groupId }
        if (dispatchEvent != null) {
            when (dispatchEvent) {
                is AgentRejectGrabEvent -> {
                    state.reject(dispatchEvent.speak, group)
                }

                is AgentFallbackEvent -> {
                    state.fallback(dispatchEvent.speak, group)
                }

                is AgentCallStartEvent -> {
                    state.callStart(dispatchEvent, group)
                }

                is AgentCallCompletionEvent -> {
                    try {
                        state.callCompletion(dispatchEvent, group)
                    } finally {
                        dispatchRef.forEach { EventBus.unsubscribeSync<AgentDispatchEvent>(it) }
                    }
                }
            }
        }
    }
    dispatchRef += dispatchSubscriber
    EventBus.subscribeAsync<AgentDispatchEvent>(state.scope, dispatchSubscriber)

    EventBus.postAsync(
        ProactiveSpeakEvent(
            botId,
            groupId,
            0.0,
            InterruptionMode.Interrupt,
            input,
            chatPointRule,
            toolSets,
            flag
        )
    )

}