package uesugi.core.state.summary

import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
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

    fun getSummariesByGroup(
        botMark: String,
        groupId: String,
        offset: Int = 0,
        limit: Int = 0
    ): Pair<List<SummaryRecord>, Int> = transaction {
        val baseQuery = SummaryEntity.find {
            (SummaryTable.botMark eq botMark) and (SummaryTable.groupId eq groupId)
        }
        val total = baseQuery.count().toInt()
        val items = if (limit > 0) {
            baseQuery.orderBy(SummaryTable.createdAt to SortOrder.DESC).limit(limit)
        } else {
            baseQuery.orderBy(SummaryTable.createdAt to SortOrder.DESC)
        }.map { it.toRecord() }
        items to total
    }

    /**
     * 获取最近一条摘要，用于生成下一段摘要时提供上下文
     */
    fun getLatestSummary(botMark: String, groupId: String): SummaryRecord? = transaction {
        SummaryEntity.find {
            (SummaryTable.botMark eq botMark) and (SummaryTable.groupId eq groupId)
        }.orderBy(SummaryTable.createdAt to SortOrder.DESC).limit(1).firstOrNull()?.toRecord()
    }

    fun getSummaryById(id: Int): SummaryRecord? = transaction {
        SummaryEntity.findById(id)?.toRecord()
    }

    fun updateSummary(
        id: Int,
        timeRange: String,
        content: String,
        keyPoints: String,
        emotionalTone: String?
    ): SummaryRecord? = transaction {
        SummaryEntity.findById(id)?.apply {
            this.timeRange = timeRange
            this.content = content
            this.keyPoints = keyPoints
            this.emotionalTone = emotionalTone
        }?.toRecord()
    }

    fun deleteSummary(id: Int): Boolean = transaction {
        val summary = SummaryEntity.findById(id)
        summary?.delete()
        summary != null
    }

    /**
     * 删除 createdAt 早于指定时间点的摘要记录
     */
    fun deleteSummariesBefore(cutoff: LocalDateTime): Int = transaction {
        val expired = SummaryEntity.find { SummaryTable.createdAt less cutoff }.toList()
        expired.forEach { it.delete() }
        expired.size
    }
}
