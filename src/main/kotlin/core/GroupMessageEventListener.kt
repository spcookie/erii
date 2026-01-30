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
import net.mamoe.mirai.utils.MiraiInternalApi
import okio.Buffer
import okio.Path.Companion.toPath
import okio.buffer
import okio.source
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.context.GlobalContext
import uesugi.ENABLE_GROUPS
import uesugi.MESSAGE_REDIRECT_GROUP_MAP
import uesugi.core.ProactiveSpeakFeature.CHAT_URGENT
import uesugi.core.ProactiveSpeakFeature.GRAB
import uesugi.core.ProactiveSpeakFeature.IGNORE_INTERRUPT
import uesugi.core.history.*
import uesugi.core.resource.ResourceEntity
import uesugi.core.resource.ResourceRecord
import uesugi.core.resource.ResourceService
import uesugi.core.resource.ResourceTable
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

    private val COMMAND_REGEX = Regex("^/([A-Za-z0-9]+)$")

    fun isCommand(text: String) = COMMAND_REGEX.matches(text)

    fun parseCommand(text: String): String? =
        COMMAND_REGEX.matchEntire(text)
            ?.destructured
            ?.component1()

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class, MiraiInternalApi::class)
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
        var format: String? = null
        val msg = buildString {
            for (singleMessage in message) {
                if (singleMessage is MessageContent) {
                    when (singleMessage) {
                        is At -> {
                            if (!isAtBot) {
                                isAtBot = singleMessage.target == botId.toLong()
                            }
                        }

                        is PlainText -> {
                        }

                        is Image -> {
                            if (url == null) {
                                messageType = MessageType.IMAGE
                                url = singleMessage.queryUrl()
                                format = singleMessage.imageType.formatName
                            }
                        }

                        else -> {
                            log.warn("Unknown message: $messageType")
                        }
                    }
                } else {
                    if (singleMessage is MessageSource) {
                        // ignore
                        continue
                    } else {
                        log.warn("Unsupported message type: {}", singleMessage)
                    }
                }
                append(singleMessage.content)
            }
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

                        val resourceRecord = transaction {
                            ResourceEntity.find { ResourceTable.md5 eq md5 }.firstOrNull()?.toRecord()
                        }

                        if (resourceRecord != null) {
                            path = resourceRecord.url
                        } else {
                            path = "./image/${groupId}/${Uuid.random().toHexString()}.${format}"

                            storage.put(
                                path.toPath(),
                                Buffer().write(buffer)
                                    .inputStream()
                                    .source()
                            )
                        }
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
                    if (route == LLMRouteRule.CHAT) {
                        EventBus.postAsync(
                            ProactiveSpeakEvent(
                                botId = botId,
                                _groupId = groupId,
                                atFromId = senderId,
                                webSearch = true,
                                chatPointRule = "",
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
            } else if (isCommand(msg)) {
                val command = parseCommand(msg)!!
                log.info("机器人收到命令 $command")
                val cmd = CmdRouteRule.from(command)
                if (cmd == null) {
                    log.warn("未知命令 $command, 跳过处理")
                } else {
                    EventBus.postAsync(
                        RouteCallEvent(
                            botId = botId,
                            groupId = groupId,
                            atFromId = senderId,
                            input = msg,
                            hit = cmd
                        )
                    )
                }
            }
        }
    }
}