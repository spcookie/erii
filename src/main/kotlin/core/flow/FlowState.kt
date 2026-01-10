package uesugi.core.flow

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object FlowStateTable : IntIdTable("flow_state") {
    val botMark = varchar("bot_mark", length = 64)
    val groupId = varchar("group_id", length = 64)
    val lastProcessedHistoryId = integer("last_processed_history_id").default(0)
    val lastProcessedAt = datetime("last_processed_at")
        .defaultExpression(CurrentDateTime)
    val currentTopic = text("current_topic").default("")
    val flowValue = double("flow_value").default(0.0)
    val lastUpdateTime = long("last_update_time").default(0)
}

class FlowStateEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<FlowStateEntity>(FlowStateTable)

    var botMark by FlowStateTable.botMark
    var groupId by FlowStateTable.groupId
    var lastProcessedHistoryId by FlowStateTable.lastProcessedHistoryId
    var lastProcessedAt by FlowStateTable.lastProcessedAt
    var currentTopic by FlowStateTable.currentTopic
    var flowValue by FlowStateTable.flowValue
    var lastUpdateTime by FlowStateTable.lastUpdateTime
}
