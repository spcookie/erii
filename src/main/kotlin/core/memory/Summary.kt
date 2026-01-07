package uesugi.core.memory

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import uesugi.core.history.HistoryTable.DEFAULT_LENGTH

object SummaryTable : IntIdTable("memory_summary") {
    val botMark = varchar("bot_mark", length = DEFAULT_LENGTH)
    val groupId = varchar("group_id", length = DEFAULT_LENGTH)
    val timeRange = varchar("time_range", length = 50)
    val content = text("content")
    val keyPoints = text("key_points")
    val emotionalTone = varchar("emotional_tone", length = 100).nullable()
    val participantCount = integer("participant_count").default(0)
    val messageCount = integer("message_count").default(0)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

class SummaryEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<SummaryEntity>(SummaryTable)

    var botMark by SummaryTable.botMark
    var groupId by SummaryTable.groupId
    var timeRange by SummaryTable.timeRange
    var content by SummaryTable.content
    var keyPoints by SummaryTable.keyPoints
    var emotionalTone by SummaryTable.emotionalTone
    var participantCount by SummaryTable.participantCount
    var messageCount by SummaryTable.messageCount
    var createdAt by SummaryTable.createdAt
}
