package uesugi.core

import kotlinx.coroutines.*
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.EventHandler
import net.mamoe.mirai.event.SimpleListenerHost
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.GroupMessageSyncEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.MessageContent
import net.mamoe.mirai.message.data.MessageSource
import net.mamoe.mirai.message.data.content
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.ENABLE_GROUPS
import uesugi.MESSAGE_REDIRECT_GROUP_MAP
import uesugi.core.history.HistoryEntity
import uesugi.core.history.HistorySavedEvent
import uesugi.core.history.MessageType
import uesugi.core.history.toRecord
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import kotlin.coroutines.CoroutineContext

object GroupMessageEventListener : SimpleListenerHost() {

    private val log = logger()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        log.error("History exception", exception)
    }

    @EventHandler
    suspend fun MessageEvent.onMessage() {
        if (this !is GroupMessageSyncEvent && this !is GroupMessageEvent) return
        if (this.group.id.toString() !in ENABLE_GROUPS) return
        val botId = bot.id.toString()
        val groupId =
            if (group.id.toString() in MESSAGE_REDIRECT_GROUP_MAP) MESSAGE_REDIRECT_GROUP_MAP.getValue(group.id.toString()) else group.id.toString()
        val senderId = sender.id.toString()
        val senderNick = sender.nameCardOrNick
        var isAtBot = false
        val msg = buildString {
            for (singleMessage in message) {
                if (singleMessage is MessageContent) {
                    appendLine(singleMessage.content)
                    if (singleMessage is At) {
                        isAtBot = true
                    }
                } else {
                    if (singleMessage is MessageSource) {
                        // ignore
                    } else {
                        log.warn("Unsupported message type: {}", singleMessage)
                    }
                }
            }
        }
        launch {
            val historyRecord = withContext(Dispatchers.IO) {
                transaction {
                    HistoryEntity.new {
                        this.botMark = botId
                        this.groupId = groupId
                        this.userId = senderId
                        this.nick = senderNick
                        this.messageType = MessageType.TEXT
                        this.content = msg
                    }.toRecord()
                }
            }
            EventBus.postAsync(HistorySavedEvent(historyRecord))
            if (isAtBot) {
                scope.launch {
                    log.info("机器人【${botId}】被@, 触发主动发言")
                    val route = RoutingAgent.route(botId, groupId, msg)
                    log.info("路由结果：{}", route)
                    if (route == RouteRule.CHAT) {
                        EventBus.postAsync(
                            ProactiveSpeakEvent(
                                botId = botId,
                                _groupId = groupId,
                                impulse = 0.0,
                                interruptionMode = InterruptionMode.Interrupt,
                                flag = ProactiveSpeakFeature.CHAT_URGENT or ProactiveSpeakFeature.GRAB or ProactiveSpeakFeature.IGNORE_INTERRUPT,
                            )
                        )
                    } else {
                        EventBus.postAsync(
                            RouteCallEvent(
                                botId = botId,
                                groupId = groupId,
                                input = msg,
                                hit = route
                            )
                        )
                    }
                }
            }
        }
        return
    }
}