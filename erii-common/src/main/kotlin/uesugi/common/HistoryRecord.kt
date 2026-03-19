package uesugi.common

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class HistoryRecord(
    var id: Int? = null,
    var botMark: String,
    var groupId: String,
    var userId: String,
    var nick: String,
    var messageType: MessageType,
    var content: String? = null,
    var resource: ResourceRecord? = null,
    var createdAt: LocalDateTime
)