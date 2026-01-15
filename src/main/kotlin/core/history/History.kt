package uesugi.core.history

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime


object HistoryTable : IntIdTable("chat_history") {
    const val DEFAULT_LENGTH = 64

    val botMark = varchar("bot_mark", length = DEFAULT_LENGTH)
    val groupId = varchar("group_id", length = DEFAULT_LENGTH)
    val userId = varchar("user_id", length = DEFAULT_LENGTH)
    val nick = varchar("nick", length = DEFAULT_LENGTH)
    val messageType = enumeration("message_type", MessageType::class)
    val content = text("content").nullable()

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)

}

enum class MessageType {
    TEXT,
    IMAGE,
    AUDIO,
    FILE,
    VIDEO,
    UNKNOWN
}

class HistoryEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<HistoryEntity>(HistoryTable)

    var botMark by HistoryTable.botMark
    var groupId by HistoryTable.groupId
    var userId by HistoryTable.userId
    var nick by HistoryTable.nick
    var messageType by HistoryTable.messageType
    var content by HistoryTable.content
    var createdAt by HistoryTable.createdAt

}

@Serializable
data class HistoryRecord(
    val id: Int,
    val botMark: String,
    val groupId: String,
    val userId: String,
    val nick: String,
    val messageType: MessageType,
    val content: String?,
    val createdAt: LocalDateTime
)

fun HistoryEntity.toRecord(): HistoryRecord {
    return HistoryRecord(
        id = id.value,
        botMark = botMark,
        groupId = groupId,
        userId = userId,
        nick = nick,
        messageType = messageType,
        content = content,
        createdAt = createdAt
    )
}