package uesugi.core.state.volition

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
import kotlin.time.ExperimentalTime

/**
 * 意愿仓库 - 负责数据库操作
 */
class VolitionRepository {

    companion object {
        private val log = logger()
    }

    /**
     * 查找需要处理的群组
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
                val volitionState = VolitionStateEntity.find(
                    (VolitionStateTable.botMark eq botMark) and (VolitionStateTable.groupId eq groupId)
                ).firstOrNull()

                val lastProcessedId = volitionState?.lastProcessedHistoryId ?: 0

                val newMessageCount = HistoryEntity.count(
                    (HistoryTable.botMark eq botMark) and
                            (HistoryTable.groupId eq groupId) and
                            (HistoryTable.id greater lastProcessedId)
                )

                newMessageCount > 0
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
     * 获取意愿状态
     */
    fun getVolitionState(botMark: String, groupId: String): VolitionStateEntity? {
        return transaction {
            VolitionStateEntity.find(
                (VolitionStateTable.botMark eq botMark) and (VolitionStateTable.groupId eq groupId)
            ).firstOrNull()
        }
    }

    /**
     * 更新意愿状态
     */
    @OptIn(ExperimentalTime::class)
    fun updateVolitionState(botMark: String, groupId: String, lastHistoryId: Int) {
        transaction {
            val now = kotlin.time.Clock.System.now()
            val tz = TimeZone.currentSystemDefault()
            val instant = now.toLocalDateTime(tz)

            val existing = VolitionStateEntity.find(
                (VolitionStateTable.botMark eq botMark) and (VolitionStateTable.groupId eq groupId)
            ).firstOrNull()

            if (existing != null) {
                existing.lastProcessedHistoryId = lastHistoryId
                existing.lastProcessedAt = instant
            } else {
                VolitionStateEntity.new {
                    this.botMark = botMark
                    this.groupId = groupId
                    this.lastProcessedHistoryId = lastHistoryId
                    this.lastProcessedAt = instant
                }
            }
            log.debug("主动意愿状态已更新, groupId=$groupId, lastHistoryId=$lastHistoryId")
        }
    }
}
