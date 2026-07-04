package uesugi.core.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uesugi.common.ChatMessage
import uesugi.common.ChatToolSet
import uesugi.onebot.core.model.MessageContent
import uesugi.onebot.sdk.client.OneBotClient
import uesugi.onebot.sdk.client.api.sendGroupMsg
import uesugi.onebot.sdk.message.buildMessage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.URL
import java.util.*
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.milliseconds

class AgentChatToolSet(
    val client: OneBotClient,
    val groupId: Long,
    val context: Context,
    private val rateLimiter: MessageSendRateLimiter = MessageSendRateLimiter()
) : ChatToolSet {

    companion object {
        private val GIF87A = "GIF87a".toByteArray()
        private val GIF89A = "GIF89a".toByteArray()
    }

    @ChatMessage
    override suspend fun sendText(texts: List<String>): String {
        try {
            for (text in texts) {
                sendGroupMessage(buildMessage { text(text) })
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
                sendGroupMessage(buildMessage {
                    image("base64://$base64")
                })
                return "发送表情包消息成功"
            } else {
                sendText(listOf(alt))
                return "未找到表情包\"$tag\"，已发送文字替代"
            }
        } catch (e: Exception) {
            return "发送表情包消息失败，原因：" + e.message
        }
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
            sendGroupMessage(buildMessage {
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
            sendGroupMessage(msg)
        } catch (e: Exception) {
            return "发送消息失败，原因：" + e.message
        }

        return "发送消息成功"
    }

    @ChatMessage
    override suspend fun sendAtAll(): String {
        try {
            sendGroupMessage(buildMessage { atAll() })
        } catch (e: Exception) {
            return "发送 At 全体成员消息失败， 原因：" + e.message
        }

        return "发送 At 全体成员消息成功"
    }

    private suspend fun sendGroupMessage(message: MessageContent) {
        rateLimiter.awaitTurn()
        client.sendGroupMsg(groupId, message)
    }

}

class MessageSendRateLimiter(
    private val intervalMs: Long = DEFAULT_SEND_INTERVAL_MS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val delayMillis: suspend (Long) -> Unit = { delay(it.milliseconds) }
) {
    private val mutex = Mutex()
    private var lastSentAtMs: Long? = null

    suspend fun awaitTurn() = mutex.withLock {
        val lastSentAt = lastSentAtMs
        if (lastSentAt != null) {
            val waitMs = intervalMs - (nowMillis() - lastSentAt)
            if (waitMs > 0) {
                delayMillis(waitMs)
            }
        }
        lastSentAtMs = nowMillis()
    }

    companion object {
        const val DEFAULT_SEND_INTERVAL_MS = 1240L
    }

}
