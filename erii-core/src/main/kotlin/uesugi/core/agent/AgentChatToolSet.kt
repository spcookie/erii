package uesugi.core.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.AtAll
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.buildMessageChain
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import uesugi.common.ChatToolSet
import java.net.URL

class AgentChatToolSet(
    val bot: Bot,
    val groupId: Long,
    val context: Context
) : ChatToolSet {

    companion object {
        private val NUMBER_PATTERN = Regex("""(?<!\d)(\d{4,})(?!\d)""")
    }

    override suspend fun sendText(texts: List<String>): String {
        try {
            for (text in texts) {
                val group = bot.getGroupOrFail(groupId)
                val memberIds = group.members.map { it.id }.toSet()

                val matches = NUMBER_PATTERN.findAll(text).toList()

                if (matches.isEmpty()) {
                    group.sendMessage(text)
                } else {
                    val messageChain = buildMessageChain {
                        var lastEnd = 0
                        for (match in matches) {
                            val precededByAt = match.range.first > 0 && text[match.range.first - 1] == '@'
                            val start = if (precededByAt) match.range.first - 1 else match.range.first

                            if (start > lastEnd) {
                                +PlainText(text.substring(lastEnd, start))
                            }
                            val userId = match.groupValues[1].toLong()
                            if (userId in memberIds) {
                                +At(userId)
                            } else {
                                +PlainText(userId.toString())
                            }
                            lastEnd = match.range.last + 1
                        }
                        if (lastEnd < text.length) {
                            +PlainText(text.substring(lastEnd))
                        }
                    }
                    group.sendMessage(messageChain)
                }
            }
        } catch (e: Exception) {
            return "消息发送失败，原因：" + e.message
        }

        return "发送文本消息成功"
    }

    override suspend fun sendMeme(tag: String, alt: String): String {
        try {
            val memo = context.meme(tag)
            if (memo != null) {
                memo.bytes.inputStream()
                    .use { image ->
                        bot.getGroupOrFail(groupId).sendImage(image)
                    }
            } else {
                sendText(listOf(alt))
            }
        } catch (e: Exception) {
            return "发送表情包消息失败，原因：" + e.message
        }

        return "发送表情包消息成功"
    }

    override suspend fun sendImageByUrl(url: String): String {
        val isImg = isImageUrl(url)
        if (!isImg) {
            return "URL 链接访问不是一个图片"
        }

        try {
            val resource = withContext(Dispatchers.IO) {
                val conn = URL(url).openConnection()
                conn.connectTimeout = 10000
                conn.readTimeout = 30000

                conn.getInputStream()
            }.use {
                it.toExternalResource()
            }
            bot.getGroupOrFail(groupId).sendImage(resource)
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

    override suspend fun sendAtAndText(
        userIds: List<Long>,
        text: String?
    ): String {
        try {
            val chain = buildMessageChain {
                for (userId in userIds) {
                    +At(userId)
                    if (text != null) {
                        +PlainText(text)
                    }
                }
            }
            bot.getGroupOrFail(groupId).sendMessage(chain)
        } catch (e: Exception) {
            return "发送消息失败，原因：" + e.message
        }

        return "发送消息成功"
    }

    override suspend fun sendAtAll(): String {
        try {
            bot.getGroupOrFail(groupId).sendMessage(AtAll)
        } catch (e: Exception) {
            return "发送 At 全体成员消息失败， 原因：" + e.message
        }

        return "发送 At 全体成员消息成功"
    }

}

object SilentToolSet : ToolSet {
    @Tool
    @LLMDescription("发送静默消息")
    fun sendSilent(): String? {
        return null
    }
}