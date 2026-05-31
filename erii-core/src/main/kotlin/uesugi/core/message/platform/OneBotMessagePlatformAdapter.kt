package uesugi.core.message.platform

import uesugi.common.data.MessageType
import uesugi.common.message.MessagePlatformAdapter
import uesugi.common.message.ParsedMessage
import uesugi.onebot.core.model.GroupMessageEvent
import uesugi.onebot.sdk.message.atQq
import uesugi.onebot.sdk.message.imageFile
import uesugi.onebot.sdk.message.imageUrl
import uesugi.onebot.sdk.message.text

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
                        if (!isAtBot && segment.atQq?.toString() == botId) {
                            isAtBot = true
                            continue
                        }
                    }

                    "image" -> {
                        if (imageUrl == null) {
                            messageType = MessageType.IMAGE
                            imageUrl = segment.imageUrl ?: segment.imageFile
                            imageFormat = segment.imageFile?.substringAfterLast(".")
                        }
                    }

                    "text" -> {
                        segment.text?.let { append(it) }
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
