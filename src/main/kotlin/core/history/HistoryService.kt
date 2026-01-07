package uesugi.core.history

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

class HistoryService {
    fun getLatestHistory(botMark: String, groupId: String, limit: Int): List<HistoryEntity> {
        return transaction {
            HistoryEntity.find {
                (HistoryTable.botMark eq botMark) and
                        (HistoryTable.groupId eq groupId)
            }.orderBy(HistoryTable.createdAt to SortOrder.DESC)
                .limit(limit)
                .reversed()
                .toList()
        }
    }
}