package uesugi.core.memory

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import uesugi.core.history.HistoryTable.DEFAULT_LENGTH

object FactsTable : IntIdTable("memory_facts") {
    val botMark = varchar("bot_mark", length = DEFAULT_LENGTH)
    val groupId = varchar("group_id", length = DEFAULT_LENGTH)
    val keyword = varchar("keyword", 255)
    val description = text("description")
    val values = text("values")
    val subjects = text("subjects")
    val scopeType = enumerationByName<Scopes>("scope_type", 50)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val validFrom = datetime("valid_from").defaultExpression(CurrentDateTime)
    val validTo = datetime("valid_to").nullable()
}

enum class Scopes {
    USER,
    GROUP
}

class FactsEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<FactsEntity>(FactsTable)

    var botMark by FactsTable.botMark
    var groupId by FactsTable.groupId
    var keyword by FactsTable.keyword
    var description by FactsTable.description
    var values by FactsTable.values
    var subjects by FactsTable.subjects
    var scopeType by FactsTable.scopeType
    var createdAt by FactsTable.createdAt
    var validFrom by FactsTable.validFrom
    var validTo by FactsTable.validTo
}