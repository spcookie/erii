package uesugi.core.message.pipeline

import kotlinx.coroutines.*
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.Buffer
import okio.Path.Companion.toPath
import okio.buffer
import okio.source
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.EventBus
import uesugi.common.data.*
import uesugi.common.message.CommandUtil
import uesugi.common.message.MessageContext
import uesugi.common.toolkit.logger
import uesugi.core.component.storage.ObjectStorage
import uesugi.core.message.history.HistorySavedEvent
import uesugi.core.message.history.HistoryService
import uesugi.core.message.resource.ResourceService
import uesugi.core.route.CmdRuleRegister
import uesugi.core.route.RouteCallEvent
import uesugi.core.route.RoutingAgent
import java.net.URL
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MessagePipeline(
    private val historyService: HistoryService,
    private val resourceService: ResourceService,
    private val storage: ObjectStorage,
) {
    private val log = logger()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun process(context: MessageContext, roleName: String) {
        scope.launch {
            saveHistoryAndPublish(context)
            routeCall(context, roleName)
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

    private fun routeCall(context: MessageContext, roleName: String) {
        val parsed = context.parsedMessage

        if (parsed.isAtBot) {
            scope.launch {
                log.info("Robot [$roleName(${context.botId})] is @, triggering active speech")
                val route = RoutingAgent.route(context.botId, context.groupId, parsed.content)
                log.info("Routing results: {}", route.name)
                EventBus.postAsync(
                    RouteCallEvent(
                        botId = context.botId,
                        groupId = context.groupId,
                        senderId = context.senderId,
                        input = "你被群友 ${context.senderNick}(${context.senderId}) @了，内容：${parsed.content}",
                        hit = route
                    )
                )
            }
        } else if (CommandUtil.isCommand(parsed.content)) {
            val command = CommandUtil.parseCommand(parsed.content)!!
            log.info("Robot receives the command $command")
            val cmd = CmdRuleRegister.getRuleForBot(command, context.botId)
            if (cmd == null) {
                log.warn("Unknown command $command, skip processing")
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
}
