package uesugi.core.state.flow

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.HistoryEntity
import uesugi.common.HistoryTable
import uesugi.common.logger

/**
 * 心流仓库 - 负责数据库操作
 */
class FlowRepository {

    companion object {
        private val log = logger()
    }

    /**
     * 查找需要处理的群组（至少需要3条新消息）
     */
    fun findGroupsNeedProcessing(botMark: String): List<String> {
        return transaction {
            val allGroupIds = HistoryTable
                .select(HistoryTable.groupId)
                .where { HistoryTable.botMark eq botMark }
                .groupBy(HistoryTable.groupId)
                .map { it[HistoryTable.groupId] }
                .distinct()

            allGroupIds.filter { groupId ->
                val flowState = FlowStateEntity.find(
                    (FlowStateTable.botMark eq botMark) and (FlowStateTable.groupId eq groupId)
                ).firstOrNull()

                val lastProcessedId = flowState?.lastProcessedHistoryId ?: 0

                val newMessageCount = HistoryEntity.count(
                    (HistoryTable.botMark eq botMark) and
                            (HistoryTable.groupId eq groupId) and
                            (HistoryTable.id greater lastProcessedId)
                )

                newMessageCount > 3
            }
        }
    }

    /**
     * 获取待处理的历史消息
     */
    fun getHistoriesToProcess(
        botMark: String,
        groupId: String,
        lastHistoryId: Int,
        limit: Int = 100
    ): List<HistoryEntity> {
        return transaction {
            HistoryEntity.find(
                (HistoryTable.botMark eq botMark) and
                        (HistoryTable.groupId eq groupId) and
                        (HistoryTable.id greater lastHistoryId)
            )
                .orderBy(HistoryTable.createdAt to SortOrder.ASC)
                .limit(limit)
                .toList()
        }
    }

    /**
     * 获取心流状态
     */
    fun getFlowState(botMark: String, groupId: String): FlowStateEntity? {
        return transaction {
            FlowStateEntity.find(
                (FlowStateTable.botMark eq botMark) and (FlowStateTable.groupId eq groupId)
            ).firstOrNull()
        }
    }

    /**
     * 更新心流状态
     */
    fun updateFlowState(botMark: String, groupId: String, lastHistoryId: Int) {
        transaction {
            val now = Clock.System.now()
            val tz = TimeZone.currentSystemDefault()
            val instant = now.toLocalDateTime(tz)

            val existing = FlowStateEntity.find(
                (FlowStateTable.botMark eq botMark) and (FlowStateTable.groupId eq groupId)
            ).firstOrNull()

            if (existing != null) {
                existing.lastProcessedHistoryId = lastHistoryId
                existing.lastProcessedAt = instant
            } else {
                FlowStateEntity.new {
                    this.botMark = botMark
                    this.groupId = groupId
                    this.lastProcessedHistoryId = lastHistoryId
                    this.lastProcessedAt = instant
                }
            }
            log.debug("心流状态已更新, groupId=$groupId, lastHistoryId=$lastHistoryId")
        }
    }
}
