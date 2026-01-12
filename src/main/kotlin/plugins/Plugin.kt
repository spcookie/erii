package plugins

import net.mamoe.mirai.contact.Group
import uesugi.core.*
import uesugi.server.BotManage
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
) {
    val roledBot = BotManage.getBot(botId) ?: return
    val group = roledBot.bot.getGroup(groupId.toLong()) ?: return

    state.init(group)

    val ref = mutableListOf<(AgentSendLifeCycleEvent) -> Unit>()

    val subscriber: (AgentSendLifeCycleEvent) -> Unit = { event ->
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
                        ref.forEach { EventBus.unsubscribeSync<AgentSendLifeCycleEvent>(it) }
                    }
                }
            }
        }
    }

    ref += subscriber

    EventBus.subscribeSync<AgentSendLifeCycleEvent>(subscriber)

    EventBus.postAsync(
        ProactiveSpeakEvent(
            botId,
            groupId,
            0.0,
            InterruptionMode.Interrupt,
            input,
            chatPointRule
        )
    )


}