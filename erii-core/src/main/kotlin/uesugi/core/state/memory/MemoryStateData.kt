package uesugi.core.state.memory

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

/**
 * 记忆处理状态表 - 记录每个群组已处理的最大 history ID
 *
 * 用于实现增量处理：
 * - lastProcessedHistoryId: 上次处理到的最大 history ID
 * - lastProcessedAt: 上次处理的时间
 */
object MemoryStateTable : IntIdTable("memory_state") {
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

/**
 * 记忆处理状态记录 - 数据传输对象
 */
@Serializable
data class MemoryStateRecord(
    val id: Int,
    val botMark: String,
    val groupId: String,
    val lastProcessedHistoryId: Int,
    val lastProcessedAt: LocalDateTime
)

/**
 * 实体转换为记录
 */
fun MemoryStateEntity.toRecord(): MemoryStateRecord = MemoryStateRecord(
    id = id.value,
    botMark = botMark,
    groupId = groupId,
    lastProcessedHistoryId = lastProcessedHistoryId,
    lastProcessedAt = lastProcessedAt
)
