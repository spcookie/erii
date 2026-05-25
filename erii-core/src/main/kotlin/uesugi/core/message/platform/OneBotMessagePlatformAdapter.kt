package uesugi.core.message.platform

import uesugi.common.data.MessageType
import uesugi.common.message.MessagePlatformAdapter
import uesugi.common.message.ParsedMessage
import uesugi.onebot.core.model.GroupMessageEvent

class OneBotMessagePlatformAdapter : MessagePlatformAdapter<GroupMessageEvent> {

    override fun extractRawGroupId(event: GroupMessageEvent): String =
        event.groupId.toString()

    override fun extractSenderId(event: GroupMessageEvent): String =
        event.userId.toString()

    override fun extractSenderNick(event: GroupMessageEvent): String =
        event.sender.card.ifBlank { event.sender.nickname }

    override suspend fun parseMessage(event: GroupMessageEvent, botId: String): ParsedMessage {
        var isAtBot = false
        var imageUrl: String? = null
        var imageFormat: String? = null
        var messageType = MessageType.TEXT

        val content = buildString {
            for (segment in event.message) {
                when (segment.type) {
                    "at" -> {
                        val qq = segment.data["qq"] ?: ""
                        if (!isAtBot && qq == botId) {
                            isAtBot = true
                            continue
                        }
                    }

                    "image" -> {
                        if (imageUrl == null) {
                            messageType = MessageType.IMAGE
                            imageUrl = segment.data["url"]
                            imageFormat = segment.data["type"]
                                ?: segment.data["file"]?.substringAfterLast(".")
                        }
                    }

                    "text" -> {
                        append(segment.data["text"] ?: "")
                    }

                    "reply" -> {
                        appendLine("---REFERENCE MESSAGE START---")
                        append("[引用消息]")
                        appendLine("---REFERENCE MESSAGE END---")
                    }
                }
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
