package plugins

import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import uesugi.core.*
import uesugi.toolkit.EventBus
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

interface Plugin {
    fun onLoad()
    fun onUnload()
}

interface SendAgentState {
    val scope: CoroutineScope
    fun sendBefore(sentences: List<String>) {}
    fun sendAfter(sentences: List<String>) {}
    fun sendReplay(sentence: String) {}
    fun sendClosed() {}
    fun sendFinally() {}
    fun dispatchReject() {}
    fun dispatchFallback() {}
    fun callStart() {}
    fun callCompletion() {}
}

@OptIn(ExperimentalUuidApi::class)
data class SendAgentConf(
    val chatPointRule: String? = null,
    val toolSets: ((ChatToolSet) -> ToolSet)? = null,
    val flag: ProactiveSpeakFeatureFlag = ProactiveSpeakFeature.NONE,
    val echo: String = Uuid.random().toHexString(),
)

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    state: SendAgentState
) = sendAgent(botId, groupId, input, SendAgentConf(), state)

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    conf: SendAgentConf = SendAgentConf(),
    state: SendAgentState
) {
    val (chatPointRule, toolSets, flag, echo) = conf
    val lifeCycleRef = mutableListOf<(AgentSendLifeCycleEvent) -> Unit>()
    val lifeCycleSubscriber: (AgentSendLifeCycleEvent) -> Unit = { event ->
        val lifeCycleEvent = event.takeIf { event.botId == botId && event.groupId == groupId && event.echo == echo }
        if (lifeCycleEvent != null) {
            when (lifeCycleEvent) {
                is AgentBeforeSendAndReceiveEvent -> state.sendBefore(lifeCycleEvent.sentences)
                is AgentAfterSendAndReceiveEvent -> state.sendAfter(lifeCycleEvent.sentences)
                is AgentReceiveReplyEvent -> state.sendReplay(lifeCycleEvent.sentence)
                is AgentSendAndReceiveClosedEvent -> state.sendClosed()
                is AgentSendAndReceiveFinallyEvent -> state.sendFinally()
            }
        }
    }
    lifeCycleRef += lifeCycleSubscriber
    EventBus.subscribeSync<AgentSendLifeCycleEvent>(lifeCycleSubscriber)

    val dispatchRef = mutableListOf<Job>()
    val dispatchSubscriber: suspend (AgentDispatchEvent) -> Unit = { event ->
        val dispatchEvent = event.takeIf { event.botId == botId && event.groupId == groupId && event.echo == echo }
        if (dispatchEvent != null) {
            when (dispatchEvent) {
                is AgentRejectGrabEvent -> {
                    state.dispatchReject()
                }

                is AgentFallbackEvent -> {
                    state.dispatchFallback()
                }

                is AgentCallStartEvent -> {
                    state.callStart()
                }

                is AgentCallCompletionEvent -> {
                    try {
                        state.callCompletion()
                    } finally {
                        dispatchRef.forEach { EventBus.unsubscribeAsync(it) }
                        lifeCycleRef.forEach { EventBus.unsubscribeSync<AgentSendLifeCycleEvent>(it) }
                    }
                }
            }
        }
    }
    dispatchRef += EventBus.subscribeAsync<AgentDispatchEvent>(state.scope, dispatchSubscriber)

    EventBus.postAsync(
        ProactiveSpeakEvent(
            botId,
            groupId,
            0.0,
            InterruptionMode.Interrupt,
            input,
            chatPointRule,
            toolSets,
            flag,
            echo
        )
    )

}