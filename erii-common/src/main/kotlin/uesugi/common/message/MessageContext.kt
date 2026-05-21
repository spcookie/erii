package uesugi.common.message

data class MessageContext(
    val botId: String,
    val groupId: String,
    val senderId: String,
    val senderNick: String,
    val parsedMessage: ParsedMessage
)
