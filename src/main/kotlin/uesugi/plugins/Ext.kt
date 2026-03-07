package uesugi.plugins

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.selectAll
import uesugi.core.message.history.HistoryRecord
import uesugi.core.message.history.HistoryTable
import uesugi.core.plugin.Database
import uesugi.core.plugin.Meta
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

fun Meta.getRefBot() = roledBot.refBot

fun Meta.getGroup() = getRefBot().getGroupOrFail(groupId.toLong())

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