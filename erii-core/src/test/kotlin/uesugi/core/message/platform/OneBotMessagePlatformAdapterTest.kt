package uesugi.core.message.platform

import kotlinx.coroutines.runBlocking
import uesugi.common.data.MessageType
import uesugi.onebot.core.model.GroupMessageEvent
import uesugi.onebot.core.model.GroupSender
import uesugi.onebot.core.model.imageSegment
import kotlin.test.Test
import kotlin.test.assertEquals

class OneBotMessagePlatformAdapterTest {

    @Test
    fun `image parser falls back to file when url is absent`() = runBlocking {
        val event = GroupMessageEvent(
            time = 0,
            selfId = 10000,
            groupId = 20000,
            userId = 30000,
            message = listOf(imageSegment(file = "https://example.com/cat.jpg")),
            sender = GroupSender(30000, "Momo"),
        )

        val parsed = OneBotMessagePlatformAdapter().parseMessage(event, "10000")

        assertEquals(MessageType.IMAGE, parsed.messageType)
        assertEquals("[图片]", parsed.content)
        assertEquals("https://example.com/cat.jpg", parsed.imageUrl)
        assertEquals("jpg", parsed.imageFormat)
    }
}
