package uesugi.core.history

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.core.resource.ResourceEntity
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

class HistoryService {
    @OptIn(ExperimentalTime::class)
    fun getLatestHistory(botMark: String, groupId: String, limit: Int, range: Duration): List<HistoryRecord> {
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
                .map {
                    it.toRecord()
                }
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
                this.resource = history.resource?.let {
                    ResourceEntity.new {
                        this.botMark = history.botMark
                        this.groupId = history.groupId
                        this.url = history.resource.url
                        this.fileName = history.resource.fileName
                        this.size = history.resource.size
                        this.md5 = history.resource.md5
                        this.createdAt = history.resource.createdAt
                    }
                }
                this.content = history.content
            }.toRecord()
        }
    }
}