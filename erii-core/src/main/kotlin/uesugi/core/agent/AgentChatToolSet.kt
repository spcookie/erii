package uesugi.core.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uesugi.common.ChatMessage
import uesugi.common.ChatToolSet
import uesugi.onebot.sdk.client.OneBotClient
import uesugi.onebot.sdk.client.api.sendGroupMsg
import uesugi.onebot.sdk.message.buildMessage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.*
import javax.imageio.ImageIO

class AgentChatToolSet(
    val client: OneBotClient,
    val groupId: Long,
    val context: Context
) : ChatToolSet {

    companion object {
        private val NUMBER_PATTERN = Regex("""(?<!\d)(\d{4,})(?!\d)""")
        private val GIF87A = "GIF87a".toByteArray()
        private val GIF89A = "GIF89a".toByteArray()
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
                val imageBytes = convertNonGifToGif(memo.bytes)
                val base64 = Base64.getEncoder().encodeToString(imageBytes)
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

    private suspend fun convertNonGifToGif(bytes: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        if (bytes.isGif()) {
            return@withContext bytes
        }

        val image = ByteArrayInputStream(bytes).use { ImageIO.read(it) }
            ?: error("Unable to read non-GIF meme image")
        val output = ByteArrayOutputStream()
        val written = output.use { ImageIO.write(image, "gif", it) }
        if (!written) {
            error("GIF image encoding is not supported")
        }
        output.toByteArray()
    }

    private fun ByteArray.isGif(): Boolean {
        return size >= GIF87A.size &&
                (startsWith(GIF87A) || startsWith(GIF89A))
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) {
            return false
        }
        for (index in prefix.indices) {
            if (this[index] != prefix[index]) {
                return false
            }
        }
        return true
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
