package uesugi.core.message.platform

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.toLocalDateTime
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.message.data.*
import net.mamoe.mirai.message.data.Image.Key.queryUrl
import net.mamoe.mirai.utils.MiraiInternalApi
import uesugi.common.data.MessageType
import uesugi.common.message.MessagePlatformAdapter
import uesugi.common.message.ParsedMessage
import uesugi.common.toolkit.DateTimeFormat
import uesugi.common.toolkit.logger
import kotlin.time.ExperimentalTime

class MiraiMessagePlatformAdapter : MessagePlatformAdapter<GroupAwareMessageEvent> {

    companion object {
        private val log = logger()
    }

    override fun extractRawGroupId(event: GroupAwareMessageEvent): String =
        event.group.id.toString()

    override fun extractSenderId(event: GroupAwareMessageEvent): String =
        event.sender.id.toString()

    override fun extractSenderNick(event: GroupAwareMessageEvent): String =
        event.sender.nameCardOrNick

    @OptIn(MiraiInternalApi::class, ExperimentalTime::class)
    override suspend fun parseMessage(event: GroupAwareMessageEvent, botId: String): ParsedMessage {
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
                            if (isAtBot) continue
                        }
                    }

                    is Image -> {
                        if (imageUrl == null) {
                            messageType = MessageType.IMAGE
                            imageUrl = singleMessage.queryUrl()
                            imageFormat = singleMessage.imageType.formatName
                        }
                    }

                    is PlainText, MessageSource -> {}

                    is QuoteReply -> {
                        appendLine("---REFERENCE MESSAGE START---")
                        val source = singleMessage.source
                        val time = Instant.fromEpochSeconds(source.time.toLong())
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                            .format(DateTimeFormat)
                        append("$time ")
                        append("${source.fromId}: ")
                        val refContent = source.originalMessage.content
                        appendLine(refContent.take(100) + if (refContent.length > 100) "..." else "")
                        appendLine("---REFERENCE MESSAGE END---")
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
}
