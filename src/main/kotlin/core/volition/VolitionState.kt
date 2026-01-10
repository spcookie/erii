package uesugi.core.volition

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object VolitionStateTable : IntIdTable("volition_state") {
    val botMark = varchar("bot_mark", length = 64)
    val groupId = varchar("group_id", length = 64)
    val fatigue = double("fatigue").default(0.0)
    val stimulus = double("stimulus").default(0.0)
    val lastActiveTime = long("last_active_time").default(0)
    val lastProcessedHistoryId = integer("last_processed_history_id").default(0)
    val lastProcessedAt = datetime("last_processed_at")
        .defaultExpression(CurrentDateTime)
}

class VolitionStateEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<VolitionStateEntity>(VolitionStateTable)

    var botMark by VolitionStateTable.botMark
    var groupId by VolitionStateTable.groupId
    var fatigue by VolitionStateTable.fatigue
    var stimulus by VolitionStateTable.stimulus
    var lastActiveTime by VolitionStateTable.lastActiveTime
    var lastProcessedHistoryId by VolitionStateTable.lastProcessedHistoryId
    var lastProcessedAt by VolitionStateTable.lastProcessedAt
}
