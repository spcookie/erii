package uesugi.core.state.evolution

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.data.HistoryTable
import uesugi.common.data.MessageType
import uesugi.common.toolkit.logger
import kotlin.time.Clock

/**
 * 进化仓库 - 负责数据库操作
 */
class EvolutionRepository {

    companion object {
        private val log = logger()
    }

    /**
     * 获取活跃群组列表
     */
    fun getActiveGroups(botMark: String): List<String> {
        return transaction {
            log.debug("开始查询活跃群组, botId=$botMark")

            val groups = queryActiveGroups(botMark)

            log.debug("查询到活跃群组数量: ${groups.size}")
            groups
        }
    }

    fun getState(botMark: String, groupId: String): EvolutionStateRecord? = transaction {
        EvolutionStateEntity.find {
            (EvolutionStateTable.botMark eq botMark) and (EvolutionStateTable.groupId eq groupId)
        }.firstOrNull()?.toStateRecord()
    }

    fun findGroupsNeedProcessing(botMark: String): List<String> = transaction {
        queryActiveGroups(botMark).filter { groupId ->
            val cursor = EvolutionStateEntity.find {
                (EvolutionStateTable.botMark eq botMark) and (EvolutionStateTable.groupId eq groupId)
            }.firstOrNull()?.lastProcessedHistoryId ?: -1
            HistoryTable.select(HistoryTable.id).where {
                (HistoryTable.botMark eq botMark) and
                        (HistoryTable.groupId eq groupId) and
                        (HistoryTable.messageType eq MessageType.TEXT) and
                        (HistoryTable.id greater cursor)
            }.limit(1).any()
        }
    }

    private fun queryActiveGroups(botMark: String): List<String> = HistoryTable
        .select(HistoryTable.groupId)
        .where { HistoryTable.botMark eq botMark }
        .groupBy(HistoryTable.groupId)
        .map { it[HistoryTable.groupId] }
        .distinct()

    fun latestHistoryId(botMark: String, groupId: String): Int? = transaction {
        HistoryTable
            .select(HistoryTable.id)
            .where { (HistoryTable.botMark eq botMark) and (HistoryTable.groupId eq groupId) }
            .orderBy(HistoryTable.id, SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.get(HistoryTable.id)
            ?.value
    }

    fun getMessagesAfter(
        botMark: String,
        groupId: String,
        cursor: Int,
        limit: Int
    ): List<EvolutionHistoryMessage> = transaction {
        HistoryTable
            .select(HistoryTable.id, HistoryTable.content)
            .where {
                (HistoryTable.botMark eq botMark) and
                        (HistoryTable.groupId eq groupId) and
                        (HistoryTable.messageType eq MessageType.TEXT) and
                        (HistoryTable.userId neq botMark) and
                        (HistoryTable.id greater cursor)
            }
            .orderBy(HistoryTable.id, SortOrder.ASC)
            .limit(limit)
            .mapNotNull { row ->
                row[HistoryTable.content]?.let { EvolutionHistoryMessage(row[HistoryTable.id].value, it) }
            }
    }

    fun updateState(botMark: String, groupId: String, cursor: Int) = transaction {
        val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
        val state = EvolutionStateEntity.find {
            (EvolutionStateTable.botMark eq botMark) and (EvolutionStateTable.groupId eq groupId)
        }.firstOrNull()
        if (state == null) {
            EvolutionStateEntity.new {
                this.botMark = botMark
                this.groupId = groupId
                lastProcessedHistoryId = cursor
                lastProcessedAt = now
            }
        } else {
            state.lastProcessedHistoryId = cursor
            state.lastProcessedAt = now
        }
    }
}
