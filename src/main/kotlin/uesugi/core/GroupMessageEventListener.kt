package uesugi.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.EventHandler
import net.mamoe.mirai.event.SimpleListenerHost
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
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
import uesugi.core.message.history.*
import uesugi.core.message.resource.ResourceEntity
import uesugi.core.message.resource.ResourceRecord
import uesugi.core.message.resource.ResourceService
import uesugi.core.message.resource.ResourceTable
import uesugi.core.route.CmdRuleRegister
import uesugi.core.route.RouteCallEvent
import uesugi.core.route.RoutingAgent
import uesugi.toolkit.EventBus
import uesugi.toolkit.ObjectStorage
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

    private val storage by GlobalContext.get().inject<ObjectStorage>()

    private val COMMAND_REGEX = Regex("^\\s*/(\\S+)(?:\\s+.*)?$")

    private val serial = mutableMapOf<String, Channel<GroupAwareMessageEvent>>()

    fun isCommand(text: String) = COMMAND_REGEX.matches(text)

    fun parseCommand(text: String): String? =
        COMMAND_REGEX.matchEntire(text)
            ?.destructured
            ?.component1()

    @EventHandler
    suspend fun MessageEvent.onMessage() {
        if (this !is GroupMessageSyncEvent && this !is GroupMessageEvent) return
        serial.computeIfAbsent(group.id.toString()) {
            val channel = Channel<GroupAwareMessageEvent>(Channel.UNLIMITED)
            scope.launch {
                for (event in channel) {
                    launch {
                        handleEvent(event)
                    }
                }
            }
            channel
        }.send(this)
    }

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class, MiraiInternalApi::class)
    suspend fun handleEvent(event: GroupAwareMessageEvent) {
        with(event) {
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
                            continue
                        } else {
                            log.warn("Unsupported message type: {}", singleMessage)
                        }
                    }
                    append(singleMessage.content)
                }
            }
            launch {
                saveAndPublishHistoryRecord(url, groupId, format, botId, senderId, senderNick, messageType, msg)
                routeCall(isAtBot, botId, groupId, msg, senderId)
            }
        }
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun routeCall(
        isAtBot: Boolean,
        botId: String,
        groupId: String,
        msg: String,
        senderId: String
    ) {
        if (isAtBot) {
            scope.launch {
                log.info("机器人[${botId}]被@, 触发主动发言")
                val route = RoutingAgent.route(botId, groupId, msg)
                log.info("路由结果：{}", route.name)
                EventBus.postAsync(
                    RouteCallEvent(
                        botId = botId,
                        groupId = groupId,
                        senderId = senderId,
                        input = "你被群友 $senderId @了，内容：$msg",
                        hit = route
                    )
                )
            }
        } else if (isCommand(msg)) {
            val command = parseCommand(msg)!!
            log.info("机器人收到命令 $command")
            val cmd = CmdRuleRegister.getRule(command)
            if (cmd == null) {
                log.warn("未知命令 $command, 跳过处理")
            } else {
                EventBus.postAsync(
                    RouteCallEvent(
                        botId = botId,
                        groupId = groupId,
                        senderId = senderId,
                        input = msg,
                        hit = cmd
                    )
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    private suspend fun saveAndPublishHistoryRecord(
        url: String?,
        groupId: String,
        format: String?,
        botId: String,
        senderId: String,
        senderNick: String,
        messageType: MessageType,
        msg: String
    ) {
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
    }
}