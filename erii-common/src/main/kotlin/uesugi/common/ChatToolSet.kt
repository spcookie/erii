package uesugi.common

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet

interface ChatToolSet : ToolSet {
    @Tool
    @LLMDescription("发送文本消息")
    suspend fun sendText(@LLMDescription("分段文本消息") texts: List<String>): String

    @Tool
    @LLMDescription("发送表情包消息")
    suspend fun sendMeme(
        @LLMDescription("表情包标签。用于向量匹配的语义标签。2-6 个字的抽象语义。") tag: String,
        @LLMDescription("表情包替代文本。若匹配不到表情包时发送的替代文本。必须是自然语言句子。") alt: String
    ): String

    @Tool
    @LLMDescription("发送图片消息")
    suspend fun sendImageByUrl(@LLMDescription("发送图片的 URL") url: String): String

    @Tool
    @LLMDescription("发送 At 消息和文本消息")
    suspend fun sendAtAndText(
        @LLMDescription("At 的用户 ID") userIds: List<Long>,
        @LLMDescription("文本消息") text: String?
    ): String

    @Tool
    @LLMDescription("发送 At 全体成员消息")
    suspend fun sendAtAll(): String

}