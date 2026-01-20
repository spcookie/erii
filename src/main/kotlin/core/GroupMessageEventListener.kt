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
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import okio.Buffer
import okio.Path.Companion.toPath
import okio.buffer
import okio.source
import org.koin.core.context.GlobalContext
import uesugi.ENABLE_GROUPS
import uesugi.MESSAGE_REDIRECT_GROUP_MAP
import uesugi.core.ProactiveSpeakFeature.CHAT_URGENT
import uesugi.core.ProactiveSpeakFeature.GRAB
import uesugi.core.ProactiveSpeakFeature.IGNORE_INTERRUPT
import uesugi.core.history.HistoryRecord
import uesugi.core.history.HistorySavedEvent
import uesugi.core.history.HistoryService
import uesugi.core.history.MessageType
import uesugi.core.resource.ResourceRecord
import uesugi.core.resource.ResourceService
import uesugi.toolkit.EventBus
import uesugi.toolkit.Storage
import uesugi.toolkit.logger
import java.net.URL
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object GroupMessageEventListener : SimpleListenerHost() {

    private val log = logger()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        log.error("History exception", exception)
    }

    private val historyService by GlobalContext.get().inject<HistoryService>()

    private val resourceService by GlobalContext.get().inject<ResourceService>()

    private val storage by GlobalContext.get().inject<Storage>()

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    @EventHandler
    suspend fun MessageEvent.onMessage() {
        if (this !is GroupMessageSyncEvent && this !is GroupMessageEvent) return
        if (this.group.id.toString() !in ENABLE_GROUPS) return
        val botId = bot.id.toString()
        val groupId =
            if (group.id.toString() in MESSAGE_REDIRECT_GROUP_MAP) {
                MESSAGE_REDIRECT_GROUP_MAP.getValue(group.id.toString())
            } else {
                group.id.toString()
            }
        val senderId = sender.id.toString()
        val senderNick = sender.nameCardOrNick
        var isAtBot = false
        var url: String? = null
        var messageType = MessageType.TEXT
        val msg = buildString {
            for (singleMessage in message) {
                if (singleMessage is MessageContent) {
                    appendLine(singleMessage.content)
                    when (singleMessage) {
                        is At -> {
                            if (!isAtBot) {
                                isAtBot = singleMessage.target == botId.toLong()
                            }
                        }

                        is Image -> {
                            if (url == null) {
                                messageType = MessageType.IMAGE
                                url = singleMessage.queryUrl()
                            }
                        }

                        else -> {
                            log.warn("Unknown message: $messageType")
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
                var resource: ResourceRecord? = null
                if (url != null) {
                    val size: Long
                    val md5: String
                    val path: String

                    URL(url).openStream().use { input ->
                        val buffer = input.source().buffer().readByteString()

                        size = buffer.size.toLong()
                        md5 = buffer.md5().hex()

                        path = "/image/${Uuid.random().toHexString()}"

                        storage.put(
                            path.toPath(),
                            Buffer().write(buffer)
                                .inputStream()
                                .source()
                        )
                    }

                    resource = resourceService.saveResource(
                        ResourceRecord(
                            botMark = botId,
                            groupId = groupId,
                            url = path,
                            fileName = path.substringAfterLast("/"),
                            size = size,
                            md5 = md5,
                            createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                        )
                    )
                }

                historyService.saveHistory(
                    HistoryRecord(
                        botMark = botId,
                        groupId = groupId,
                        userId = senderId,
                        nick = senderNick,
                        messageType = messageType,
                        content = msg,
                        resource = resource,
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
                                flag = CHAT_URGENT or GRAB or IGNORE_INTERRUPT,
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
    }
}