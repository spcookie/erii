package uesugi.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mamoe.mirai.event.EventHandler
import net.mamoe.mirai.event.SimpleListenerHost
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.GroupMessageSyncEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageContent
import net.mamoe.mirai.message.data.content
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.core.history.HistoryEntity
import uesugi.core.history.HistorySavedEvent
import uesugi.core.history.MessageType
import uesugi.core.history.toRecord
import uesugi.core.volition.InterruptionMode
import uesugi.core.volition.ProactiveSpeakEvent
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import kotlin.coroutines.CoroutineContext

object GroupMessageEventListener : SimpleListenerHost() {

    private val log = logger()

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        log.error("History exception", exception)
    }

    @EventHandler
    suspend fun MessageEvent.onMessage() {
        if (this !is GroupMessageSyncEvent && this !is GroupMessageEvent) return
        val botId = bot.id.toString()
        val groupId = if (group.id.toString() == "474270623") "1053148332" else group.id.toString()
        val senderId = sender.id.toString()
        var isAtBot = false
        val msg = buildString {
            for (singleMessage in message) {
                if (singleMessage is MessageContent) {
                    appendLine(singleMessage.content)
                    if (singleMessage is At) {
                        isAtBot = true
                    }
                } else {
                    log.warn("Unsupported message type: {}", singleMessage)
                }
            }
        }
        launch {
            val historyRecord = withContext(Dispatchers.IO) {
                transaction {
                    HistoryEntity.Companion.new {
                        this.botMark = botId
                        this.groupId = groupId
                        this.userId = senderId
                        this.messageType = MessageType.TEXT
                        this.content = msg
                    }.toRecord()
                }
            }
            EventBus.postAsync(HistorySavedEvent(historyRecord))
            if (isAtBot) {
                log.info("机器人【${botId}】被@, 触发主动发言")
                EventBus.postAsync(
                    ProactiveSpeakEvent(
                        botMark = botId,
                        groupId = groupId,
                        impulse = 0.0,
                        interruptionMode = InterruptionMode.Interrupt
                    )
                )
            }
        }
        return
    }
}