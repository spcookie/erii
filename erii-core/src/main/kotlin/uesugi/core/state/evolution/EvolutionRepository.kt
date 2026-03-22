package uesugi.core.state.evolution

import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.HistoryTable
import uesugi.common.logger

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

            val groups = HistoryTable
                .select(HistoryTable.groupId)
                .where { HistoryTable.botMark eq botMark }
                .groupBy(HistoryTable.groupId)
                .map { it[HistoryTable.groupId] }
                .distinct()

            log.debug("查询到活跃群组数量: ${groups.size}")
            groups
        }
    }
}
