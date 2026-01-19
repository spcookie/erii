package uesugi.core

import kotlinx.coroutines.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.EventHandler
import net.mamoe.mirai.event.SimpleListenerHost
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.GroupMessageSyncEvent
import net.mamoe.mirai.event.events.MessageEvent
import net.mamoe.mirai.message.data.*
import org.koin.core.context.GlobalContext
import uesugi.ENABLE_GROUPS
import uesugi.MESSAGE_REDIRECT_GROUP_MAP
import uesugi.core.history.HistoryRecord
import uesugi.core.history.HistorySavedEvent
import uesugi.core.history.HistoryService
import uesugi.core.history.MessageType
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object GroupMessageEventListener : SimpleListenerHost() {

    private val log = logger()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        log.error("History exception", exception)
    }

    private val historyService by GlobalContext.get().inject<HistoryService>()

    @OptIn(ExperimentalTime::class)
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
                    when (singleMessage) {
                        is At -> {
                            isAtBot = true
                        }

                        is Image -> {

                        }
                    }
                } else {
                    if (singleMessage is MessageSource) {
                        // ignore
                    } else {
                        log.warn("Unsupported message type: {}", singleMessage)
                    }
                }
            }
            deleteAt(length - 1)
        }
        launch {
            val historyRecord = withContext(Dispatchers.IO) {
                historyService.saveHistory(
                    HistoryRecord(
                        botMark = botId,
                        groupId = groupId,
                        userId = senderId,
                        nick = senderNick,
                        messageType = MessageType.TEXT,
                        content = msg,
                        createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    )
                )
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
                                atFromId = senderId,
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
                                atFromId = senderId,
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