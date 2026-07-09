package uesugi.core.message.platform

import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uesugi.common.data.MessageType
import uesugi.common.message.MessagePlatformAdapter
import uesugi.common.message.ParsedMessage
import uesugi.onebot.core.model.GroupMessageEvent
import uesugi.onebot.core.message.*

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
                        }
                        append("@${segment.atQq}")
                    }

                    "image" -> {
                        if (imageUrl == null) {
                            messageType = MessageType.IMAGE
                            imageUrl = segment.imageUrl ?: segment.imageFile
                            imageFormat = segment.imageFile?.substringAfterLast(".")
                        }
                        append(segment.data["summary"]?.jsonPrimitive?.contentOrNull ?: "[图片]")
                    }

                    "text" -> {
                        segment.text?.let { append(it) }
                    }

                    "face" -> {
                        val face = segment.data["row"]?.jsonObject["faceText"]?.jsonPrimitive?.contentOrNull ?: ""
                        append("[${face.removePrefix("/")}]")
                    }

                    "reply" -> {
                        append("[引用消息 id: ${segment.replyId}]")
                    }

                    else -> append("[${segment.type}]")
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
