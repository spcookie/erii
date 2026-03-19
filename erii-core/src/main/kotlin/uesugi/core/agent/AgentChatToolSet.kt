package uesugi.core.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.message.data.At
import uesugi.common.ChatToolSet

class QQChatToolSet(
    val bot: Bot,
    val groupId: Long,
    val context: Context
) : ChatToolSet {
    override suspend fun sendText(text: String): String {
        bot.getGroupOrFail(groupId).sendMessage(text)
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