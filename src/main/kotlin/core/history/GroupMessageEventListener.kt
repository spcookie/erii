package uesugi.core.history

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.mamoe.mirai.event.EventHandler
import net.mamoe.mirai.event.SimpleListenerHost
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.GroupMessageSyncEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.MessageContent
import net.mamoe.mirai.message.data.content
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
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
        val groupId = group.id.toString()
        val senderId = sender.id.toString()
        val msg = buildString {
            for (singleMessage in message) {
                if (singleMessage is MessageContent) {
                    appendLine(singleMessage.content)
                } else {
                    log.info("Unsupported message type: {}", singleMessage)
                }
            }
        }
        launch {
            val historyEntity = withContext(Dispatchers.IO) {
                transaction {
                    HistoryEntity.new {
                        this.botMark = botId
                        this.groupId = groupId
                        this.userId = senderId
                        this.messageType = MessageType.TEXT
                        this.content = msg
                    }
                }
            }
            EventBus.postAsync(HistorySavedEvent(historyEntity))
        }
        return
    }
}