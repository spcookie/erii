package uesugi.core.memory

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import uesugi.core.history.HistoryTable.DEFAULT_LENGTH

object TodoTable : IntIdTable("memory_todo") {
    val botMark = varchar("bot_mark", length = DEFAULT_LENGTH)
    val content = text("content")
    val priority = integer("priority").default(5)
    val status = enumerationByName<TodoStatus>("status", 50).default(TodoStatus.PENDING)
    val category = varchar("category", length = 100).nullable()
    val relatedUserId = varchar("related_user_id", length = 64).nullable()
    val relatedGroupId = varchar("related_group_id", length = 64).nullable()
    val attempts = integer("attempts").default(0)
    val lastAttemptAt = datetime("last_attempt_at").nullable()
    val expireAt = datetime("expire_at").nullable()
    val fulfilledAt = datetime("fulfilled_at").nullable()
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

enum class TodoStatus {
    PENDING,
    IN_PROGRESS,
    FULFILLED,
    EXPIRED,
    CANCELLED
}

class TodoEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TodoEntity>(TodoTable)

    var botMark by TodoTable.botMark
    var content by TodoTable.content
    var priority by TodoTable.priority
    var status by TodoTable.status
    var category by TodoTable.category
    var relatedUserId by TodoTable.relatedUserId
    var relatedGroupId by TodoTable.relatedGroupId
    var attempts by TodoTable.attempts
    var lastAttemptAt by TodoTable.lastAttemptAt
    var expireAt by TodoTable.expireAt
    var fulfilledAt by TodoTable.fulfilledAt
    var createdAt by TodoTable.createdAt
    var updatedAt by TodoTable.updatedAt
}
