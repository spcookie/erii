package uesugi.core.memory

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

/**
 * 记忆处理状态表 - 记录每个群组已处理的最大 history ID
 */
object MemoryStateTable : org.jetbrains.exposed.v1.core.dao.id.IntIdTable("memory_state") {
    val botMark = varchar("bot_mark", length = 64)
    val groupId = varchar("group_id", length = 64)
    val lastProcessedHistoryId = integer("last_processed_history_id").default(0)
    val lastProcessedAt = datetime("last_processed_at")
        .defaultExpression(CurrentDateTime)
}

/**
 * 记忆处理状态实体
 */
class MemoryStateEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MemoryStateEntity>(MemoryStateTable)

    var botMark by MemoryStateTable.botMark
    var groupId by MemoryStateTable.groupId
    var lastProcessedHistoryId by MemoryStateTable.lastProcessedHistoryId
    var lastProcessedAt by MemoryStateTable.lastProcessedAt
}