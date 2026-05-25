package uesugi.spi

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import uesugi.common.BotManage
import uesugi.common.data.HistoryRecord
import uesugi.common.data.HistoryTable
import uesugi.common.toolkit.ConfigHolder
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

fun Meta.getRefBot() = roledBot.refBot

fun Meta.getAdmins() = ConfigHolder.getAdmins(BotManage.getConfigKey(botId), groupId)

fun Meta.isAdmin() = senderId in getAdmins()

@OptIn(ExperimentalTime::class)
suspend fun Database.getLatestHistory(
    botId: String,
    groupId: String,
    limit: Int,
    range: Duration
): List<HistoryRecord> {
    val now = Clock.System.now()
    val oneDayAgo = now - range
    val timeZone = TimeZone.currentSystemDefault()
    return getHistory {
        HistoryTable.selectAll()
            .where {
                (HistoryTable.botMark eq botId) and
                        (HistoryTable.groupId eq groupId) and
                        (HistoryTable.createdAt greaterEq oneDayAgo.toLocalDateTime(timeZone)) and
                        (HistoryTable.createdAt lessEq now.toLocalDateTime(timeZone))
            }
            .orderBy(HistoryTable.createdAt to SortOrder.DESC)
            .limit(limit)
    }.reversed()
}