package uesugi.common.message

interface MessagePlatformAdapter<E : Any> {
    fun extractRawGroupId(event: E): String
    fun extractSenderId(event: E): String
    fun extractSenderNick(event: E): String
    suspend fun parseMessage(event: E, botId: String): ParsedMessage
}
