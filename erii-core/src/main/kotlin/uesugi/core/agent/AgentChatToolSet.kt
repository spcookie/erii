package uesugi.core.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.PlainText
import net.mamoe.mirai.message.data.buildMessageChain
import uesugi.common.ChatToolSet

class QQChatToolSet(
    val bot: Bot,
    val groupId: Long,
    val context: Context
) : ChatToolSet {

    private val AT_PATTERN = Regex("""\[at:(\d+)]""")

    override suspend fun sendText(text: String): String {
        val matches = AT_PATTERN.findAll(text).toList()

        if (matches.isEmpty()) {
            // 没有 @ 标记，直接发送纯文本
            bot.getGroupOrFail(groupId).sendMessage(text)
        } else {
            // 构建包含 At 消息段的 MessageChain
            val messageChain = buildMessageChain {
                var lastEnd = 0
                for (match in matches) {
                    // 添加 @ 之前的文本
                    if (match.range.first > lastEnd) {
                        +PlainText(text.substring(lastEnd, match.range.first))
                    }
                    // 添加 At 消息段
                    val userId = match.groupValues[1].toLong()
                    +At(userId)
                    lastEnd = match.range.last + 1
                }
                // 添加最后一个 @ 之后的文本
                if (lastEnd < text.length) {
                    +PlainText(text.substring(lastEnd))
                }
            }
            bot.getGroupOrFail(groupId).sendMessage(messageChain)
        }
        return "发送文本消息成功"
    }

    override suspend fun sendMeme(tag: String, alt: String): String {
        val memo = context.memo(tag)
        if (memo != null) {
            memo.bytes.inputStream()
                .use { image ->
                    bot.getGroupOrFail(groupId).sendImage(image)
                }
        } else {
            sendText(alt)
        }

        return "发送表情包消息成功"
    }

    override suspend fun sendAt(userId: Long): String {
        bot.getGroupOrFail(groupId).sendMessage(At(userId))

        return "发送 At 消息成功"
    }

}

object SilentToolSet : ToolSet {
    @Tool
    @LLMDescription("发送静默消息")
    fun sendSilent(): String? {
        return null
    }
}