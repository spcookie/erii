package uesugi.core.state.summary

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * 摘要仓库 - 负责摘要相关的数据库操作
 */
class SummaryRepository {

    /**
     * 保存对话摘要
     */
    fun saveSummary(
        botMark: String,
        groupId: String,
        timeRange: String,
        content: String,
        keyPoints: String,
        emotionalTone: String?,
        participantCount: Int,
        messageCount: Int
    ) {
        transaction {
            SummaryEntity.new {
                this.botMark = botMark
                this.groupId = groupId
                this.timeRange = timeRange
                this.content = content
                this.keyPoints = keyPoints
                this.emotionalTone = emotionalTone
                this.participantCount = participantCount
                this.messageCount = messageCount
            }
        }
    }

    fun getSummariesByGroup(botMark: String, groupId: String): List<SummaryRecord> = transaction {
        SummaryEntity.find {
            (SummaryTable.botMark eq botMark) and (SummaryTable.groupId eq groupId)
        }.orderBy(SummaryTable.createdAt to SortOrder.DESC).map { it.toRecord() }
    }

    fun getSummaryById(id: Int): SummaryRecord? = transaction {
        SummaryEntity.findById(id)?.toRecord()
    }

    fun updateSummary(
        id: Int,
        timeRange: String,
        content: String,
        keyPoints: String,
        emotionalTone: String?,
        participantCount: Int,
        messageCount: Int
    ): SummaryRecord? = transaction {
        SummaryEntity.findById(id)?.apply {
            this.timeRange = timeRange
            this.content = content
            this.keyPoints = keyPoints
            this.emotionalTone = emotionalTone
            this.participantCount = participantCount
            this.messageCount = messageCount
        }?.toRecord()
    }

    fun deleteSummary(id: Int): Boolean = transaction {
        val summary = SummaryEntity.findById(id)
        summary?.delete()
        summary != null
    }
}
