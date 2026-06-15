package uesugi.core.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uesugi.common.ChatMessage
import uesugi.common.ChatToolSet
import uesugi.onebot.sdk.client.OneBotClient
import uesugi.onebot.sdk.client.api.sendGroupMsg
import uesugi.onebot.sdk.message.buildMessage
import java.net.URL
import java.util.*

class AgentChatToolSet(
    val client: OneBotClient,
    val groupId: Long,
    val context: Context
) : ChatToolSet {

    companion object {
        private val NUMBER_PATTERN = Regex("""(?<!\d)(\d{4,})(?!\d)""")
    }

    @ChatMessage
    override suspend fun sendText(texts: List<String>): String {
        try {
            for (text in texts) {
                val matches = NUMBER_PATTERN.findAll(text).toList()

                if (matches.isEmpty()) {
                    client.sendGroupMsg(groupId, buildMessage { text(text) })
                } else {
                    val msg = buildMessage {
                        var lastEnd = 0
                        for (match in matches) {
                            val precededByAt = match.range.first > 0 && text[match.range.first - 1] == '@'
                            val start = if (precededByAt) match.range.first - 1 else match.range.first

                            if (start > lastEnd) {
                                text(text.substring(lastEnd, start))
                            }
                            val userId = match.groupValues[1].toLong()
                            at(userId)
                            lastEnd = match.range.last + 1
                        }
                        if (lastEnd < text.length) {
                            text(text.substring(lastEnd))
                        }
                    }
                    client.sendGroupMsg(groupId, msg)
                }
            }
        } catch (e: Exception) {
            return "消息发送失败，原因：" + e.message
        }

        return "发送文本消息成功"
    }

    @ChatMessage
    override suspend fun sendMeme(tag: String, alt: String): String {
        try {
            val memo = context.meme(tag)
            if (memo != null) {
                val base64 = Base64.getEncoder().encodeToString(memo.bytes)
                client.sendGroupMsg(groupId, buildMessage {
                    image("base64://$base64")
                })
            } else {
                sendText(listOf(alt))
            }
        } catch (e: Exception) {
            return "发送表情包消息失败，原因：" + e.message
        }

        return "发送表情包消息成功"
    }

    @ChatMessage
    override suspend fun sendImageByUrl(url: String): String {
        val isImg = isImageUrl(url)
        if (!isImg) {
            return "URL 链接访问不是一个图片"
        }

        try {
            client.sendGroupMsg(groupId, buildMessage {
                image(file = url)
            })
        } catch (e: Exception) {
            return "发送图片失败，原因：" + e.message
        }

        return "发送图片成功"
    }

    private suspend fun isImageUrl(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection()
            conn.connectTimeout = 10000
            conn.readTimeout = 30000

            val contentType = conn.contentType ?: return@withContext false

            contentType.startsWith("image/")
        } catch (_: Exception) {
            false
        }
    }

    @ChatMessage
    override suspend fun sendAtAndText(
        userIds: List<Long>,
        text: String?
    ): String {
        try {
            val msg = buildMessage {
                for (userId in userIds) {
                    at(userId)
                }
                text?.let { text(it) }
            }
            client.sendGroupMsg(groupId, msg)
        } catch (e: Exception) {
            return "发送消息失败，原因：" + e.message
        }

        return "发送消息成功"
    }

    @ChatMessage
    override suspend fun sendAtAll(): String {
        try {
            client.sendGroupMsg(groupId, buildMessage { atAll() })
        } catch (e: Exception) {
            return "发送 At 全体成员消息失败， 原因：" + e.message
        }

        return "发送 At 全体成员消息成功"
    }

}

object SilentToolSet : ToolSet {
    @ChatMessage
    @Tool
    @LLMDescription("本次选择不发言。当你判断不需要回复时，必须调用此工具结束交互，禁止直接返回文本。")
    fun sendSilent(): String? {
        return null
    }
}
