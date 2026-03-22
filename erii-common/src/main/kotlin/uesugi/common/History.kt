package uesugi.common

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import uesugi.core.message.resource.ResourceEntity
import uesugi.core.message.resource.ResourceRecord
import uesugi.core.message.resource.ResourceTable


object HistoryTable : IntIdTable("chat_history") {
    const val DEFAULT_LENGTH = 64

    val botMark = varchar("bot_mark", length = DEFAULT_LENGTH)
    val groupId = varchar("group_id", length = DEFAULT_LENGTH)
    val userId = varchar("user_id", length = DEFAULT_LENGTH)
    val nick = varchar("nick", length = DEFAULT_LENGTH)
    val messageType = enumeration("message_type", MessageType::class)
    val content = text("content").nullable()

    val resourceId = optReference("resource_id", ResourceTable)

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}


class HistoryEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<HistoryEntity>(HistoryTable)

    var botMark by HistoryTable.botMark
    var groupId by HistoryTable.groupId
    var userId by HistoryTable.userId
    var nick by HistoryTable.nick
    var messageType by HistoryTable.messageType
    var content by HistoryTable.content

    var resource by ResourceEntity optionalReferencedOn HistoryTable.resourceId

    var createdAt by HistoryTable.createdAt
}

enum class MessageType {
    TEXT,
    IMAGE,
    AUDIO,
    FILE,
    VIDEO,
    UNKNOWN
}

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

fun ResourceEntity.toRecord(): ResourceRecord {
    return ResourceRecord(
        id = id.value,
        botMark = botMark,
        groupId = groupId,
        url = url,
        fileName = fileName,
        size = size,
        createdAt = createdAt,
        md5 = md5,
    )
}

fun HistoryEntity.toRecord(): HistoryRecord {
    return HistoryRecord(
        id = id.value,
        botMark = botMark,
        groupId = groupId,
        userId = userId,
        nick = nick,
        messageType = messageType,
        content = content,
        resource = resource?.toRecord(),
        createdAt = createdAt
    )
}
