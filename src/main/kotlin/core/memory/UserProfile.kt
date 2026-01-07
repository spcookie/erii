package uesugi.core.memory

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import uesugi.core.history.HistoryTable.DEFAULT_LENGTH

object UserProfileTable : IntIdTable("memory_user_profile") {
    val botMark = varchar("bot_mark", length = DEFAULT_LENGTH)
    val groupId = varchar("group_id", length = DEFAULT_LENGTH)
    val userId = varchar("user_id", length = 64)
    val profile = text("profile")
    val preferences = text("preferences")
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

class UserProfileEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<UserProfileEntity>(UserProfileTable)

    var botMark by UserProfileTable.botMark
    var groupId by UserProfileTable.groupId
    var userId by UserProfileTable.userId
    var profile by UserProfileTable.profile
    var preferences by UserProfileTable.preferences
    var createdAt by UserProfileTable.createdAt
}