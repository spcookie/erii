package uesugi.core.state.memory

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import uesugi.core.state.emotion.EmotionTable.DEFAULT_LENGTH

/**
 * 事实记忆表 - 存储从群聊中提取的事实信息
 *
 * 字段说明：
 * - keyword: 关键词，用于快速检索
 * - description: 事实的自然语言描述
 * - values: 相关值/属性（如地点，职业等）
 * - subjects: 涉及的主体（用户ID列表）
 * - scopeType: 作用范围（个人/群组）
 * - validFrom/validTo: 有效时间范围（用于实现事实的动态更新）
 */
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
    val vectorId = varchar("vector_id", length = 64).nullable()
}

/**
 * 记忆作用范围枚举
 */
enum class Scopes {
    @LLMDescription("个人属性 USER")
    USER,

    @LLMDescription("群组共识 GROUP")
    GROUP
}

/**
 * 事实记忆实体
 */
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
    var vectorId by FactsTable.vectorId
}

/**
 * 事实记忆记录 - 数据传输对象
 */
@Serializable
data class FactsRecord(
    val id: Int,
    val botMark: String,
    val groupId: String,
    val keyword: String,
    val description: String,
    val values: String,
    val subjects: String,
    val scopeType: Scopes,
    val createdAt: LocalDateTime,
    val validFrom: LocalDateTime,
    val validTo: LocalDateTime?,
    val vectorId: String? = null
)

/**
 * 实体转换为记录
 */
fun FactsEntity.toRecord(): FactsRecord = FactsRecord(
    id = id.value,
    botMark = botMark,
    groupId = groupId,
    keyword = keyword,
    description = description,
    values = values,
    subjects = subjects,
    scopeType = scopeType,
    createdAt = createdAt,
    validFrom = validFrom,
    validTo = validTo,
    vectorId = vectorId
)
