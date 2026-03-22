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
import uesugi.common.*
import uesugi.core.component.ObjectStorage
import uesugi.core.message.history.HistorySavedEvent
import uesugi.core.message.history.HistoryService
import uesugi.core.message.resource.ResourceEntity
import uesugi.core.message.resource.ResourceRecord
import uesugi.core.message.resource.ResourceService
import uesugi.core.message.resource.ResourceTable
import uesugi.core.route.CmdRuleRegister
import uesugi.core.route.RouteCallEvent
import uesugi.core.route.RoutingAgent
import java.net.URL
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * 解析后的消息内容
 */
private data class ParsedMessage(
    val content: String,
    val isAtBot: Boolean,
    val messageType: MessageType,
    val imageUrl: String?,
    val imageFormat: String?
)

/**
 * 消息上下文信息
 */
private data class MessageContext(
    val botId: String,
    val groupId: String,
    val senderId: String,
    val senderNick: String,
    val parsedMessage: ParsedMessage
)

class GroupMessageEventListener(
    private val botId: String,
    private val roleName: String
) : SimpleListenerHost() {

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

    private fun channelKey(groupId: String) = "${botId}_$groupId"

    fun isCommand(text: String) = COMMAND_REGEX.matches(text)

    fun parseCommand(text: String): String? =
        COMMAND_REGEX.matchEntire(text)
            ?.destructured
            ?.component1()

    @EventHandler
    suspend fun MessageEvent.onMessage() {
        if (this !is GroupMessageSyncEvent && this !is GroupMessageEvent) return
        // 只处理属于当前监听器 bot 的消息
        if (this.bot.id.toString() != botId) return

        serial.computeIfAbsent(channelKey(group.id.toString())) {
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
        val groupId = resolveGroupId(event.group.id.toString())
        if (groupId !in ENABLE_GROUPS) return

        val context = buildMessageContext(event, botId, groupId)

        launch {
            saveHistoryAndPublish(context)
            routeCall(context)
        }
    }

    private fun resolveGroupId(rawGroupId: String): String {
        return MESSAGE_REDIRECT_GROUP_MAP.getOrDefault(rawGroupId, rawGroupId)
    }

    private suspend fun buildMessageContext(
        event: GroupAwareMessageEvent,
        botId: String,
        groupId: String
    ): MessageContext {
        val parsed = parseMessage(event, botId)
        return MessageContext(
            botId = botId,
            groupId = groupId,
            senderId = event.sender.id.toString(),
            senderNick = event.sender.nameCardOrNick,
            parsedMessage = parsed
        )
    }

    @OptIn(MiraiInternalApi::class)
    private suspend fun parseMessage(event: GroupAwareMessageEvent, botId: String): ParsedMessage {
        var isAtBot = false
        var imageUrl: String? = null
        var imageFormat: String? = null
        var messageType = MessageType.TEXT

        val content = buildString {
            for (singleMessage in event.message) {
                when (singleMessage) {
                    is At -> {
                        if (!isAtBot) {
                            isAtBot = singleMessage.target == botId.toLong()
                        }
                    }

                    is Image -> {
                        if (imageUrl == null) {
                            messageType = MessageType.IMAGE
                            imageUrl = singleMessage.queryUrl()
                            imageFormat = singleMessage.imageType.formatName
                        }
                    }

                    is PlainText -> { /* 纯文本，直接追加内容 */
                    }

                    is MessageSource -> { /* 消息源，跳过 */
                    }

                    else -> {
                        log.warn("Unsupported message type: {}", singleMessage)
                    }
                }
                append(singleMessage.content)
            }
        }

        return ParsedMessage(
            content = content,
            isAtBot = isAtBot,
            messageType = messageType,
            imageUrl = imageUrl,
            imageFormat = imageFormat
        )
    }

    private fun routeCall(context: MessageContext) {
        val parsed = context.parsedMessage

        if (parsed.isAtBot) {
            scope.launch {
                log.info("机器人[$roleName(${context.botId})]被@, 触发主动发言")
                val route = RoutingAgent.route(context.botId, context.groupId, parsed.content)
                log.info("路由结果：{}", route.name)
                EventBus.postAsync(
                    RouteCallEvent(
                        botId = context.botId,
                        groupId = context.groupId,
                        senderId = context.senderId,
                        input = "你被群友 ${context.senderId} @了，内容：${parsed.content}",
                        hit = route
                    )
                )
            }
        } else if (isCommand(parsed.content)) {
            val command = parseCommand(parsed.content)!!
            log.info("机器人收到命令 $command")
            val cmd = CmdRuleRegister.getRule(command)
            if (cmd == null) {
                log.warn("未知命令 $command, 跳过处理")
            } else {
                EventBus.postAsync(
                    RouteCallEvent(
                        botId = context.botId,
                        groupId = context.groupId,
                        senderId = context.senderId,
                        input = parsed.content,
                        hit = cmd
                    )
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class, ExperimentalUuidApi::class)
    private suspend fun saveHistoryAndPublish(context: MessageContext) {
        val parsed = context.parsedMessage
        val historyRecord = withContext(Dispatchers.IO) {
            var resource: ResourceRecord? = null

            if (parsed.imageUrl != null) {
                val imageUrl = parsed.imageUrl
                val format = parsed.imageFormat

                val size: Long
                val md5: String
                val path: String

                URL(imageUrl).openStream().use { input ->
                    val buffer = input.source().buffer().readByteString()

                    size = buffer.size.toLong()
                    md5 = buffer.md5().hex()

                    val resourceRecord = transaction {
                        ResourceEntity.find { ResourceTable.md5 eq md5 }.firstOrNull()?.toRecord()
                    }

                    if (resourceRecord != null) {
                        path = resourceRecord.url
                    } else {
                        path = "./image/${context.groupId}/${Uuid.random().toHexString()}.${format}"

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
                        botMark = context.botId,
                        groupId = context.groupId,
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
                    botMark = context.botId,
                    groupId = context.groupId,
                    userId = context.senderId,
                    nick = context.senderNick,
                    messageType = parsed.messageType,
                    content = parsed.content,
                    resource = resource,
                    createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                )
            )
        }
        EventBus.postAsync(HistorySavedEvent(context.parsedMessage.isAtBot, historyRecord))
    }
}