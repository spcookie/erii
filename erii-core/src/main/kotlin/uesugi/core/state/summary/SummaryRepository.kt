package uesugi.core.state.summary

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
}
