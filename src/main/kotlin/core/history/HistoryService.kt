package uesugi.core.history

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class HistoryService {
    @OptIn(ExperimentalTime::class)
    fun getLatestHistory(botMark: String, groupId: String, limit: Int, range: Duration): List<HistoryEntity> {
        val now = Clock.System.now()
        val oneDayAgo = now - range
        val timeZone = TimeZone.currentSystemDefault()
        return transaction {
            HistoryEntity.find {
                (HistoryTable.botMark eq botMark) and
                        (HistoryTable.groupId eq groupId) and
                        (HistoryTable.createdAt greaterEq oneDayAgo.toLocalDateTime(timeZone)) and
                        (HistoryTable.createdAt lessEq now.toLocalDateTime(timeZone))
            }.orderBy(HistoryTable.createdAt to SortOrder.DESC)
                .limit(limit)
                .reversed()
                .toList()
        }
    }

    fun saveHistory(history: HistoryRecord): HistoryRecord {
        return transaction {
            HistoryEntity.new {
                this.botMark = history.botMark
                this.groupId = history.groupId
                this.userId = history.userId
                this.nick = history.nick
                this.messageType = MessageType.TEXT
                this.content = history.content
            }.toRecord()
        }
    }
}