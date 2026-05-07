@file:UseSerializers(LocalDateTimeAsDateSerializer::class)

package uesugi.core.state.memory

import ai.koog.agents.core.tools.annotations.LLMDescription
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import uesugi.common.toolkit.LocalDateTimeAsDateSerializer
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
@Serializable(with = ScopesSerializer::class)
enum class Scopes {
    @LLMDescription("个人属性 USER")
    USER,

    @LLMDescription("群组共识 GROUP")
    GROUP
}

object ScopesSerializer : KSerializer<Scopes> {
    override val descriptor = PrimitiveSerialDescriptor("Scopes", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): Scopes {
        return when (decoder.decodeString().lowercase()) {
            "user" -> Scopes.USER
            "group" -> Scopes.GROUP
            else -> throw IllegalArgumentException("Unknown scope: ${decoder.decodeString()}")
        }
    }

    override fun serialize(encoder: Encoder, value: Scopes) {
        encoder.encodeString(value.name.lowercase())
    }
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

// ==================== 事实提取与冲突解决数据模型 ====================

/**
 * LLM 提取的原始事实
 */
@Serializable
@LLMDescription("从消息中提取的事实")
data class ExtractedFact(
    @property:LLMDescription("关键词，2-6个字")
    val keyword: String,
    @property:LLMDescription("事实的自然语言描述，20-50字")
    val description: String,
    @property:LLMDescription("相关值/属性，如地点名称、职业名称等")
    val values: String = "",
    @property:LLMDescription("涉及的用户ID，逗号分隔")
    val subjects: String = "",
    @property:LLMDescription("范围类型: user 或 group")
    val scope: Scopes = Scopes.USER
)

/**
 * 事实提取结果
 */
@Serializable
@LLMDescription("事实提取结果")
data class FactExtractionResult(
    @property:LLMDescription("提取出的事实列表")
    val facts: List<ExtractedFact>
)

/**
 * 记忆操作类型
 */
@Serializable(with = MemoryActionSerializer::class)
enum class MemoryAction {
    @LLMDescription("添加新事实")
    ADD,

    @LLMDescription("更新已有事实")
    UPDATE,

    @LLMDescription("废弃过时事实")
    DELETE,

    @LLMDescription("无需操作")
    NONE
}

object MemoryActionSerializer : KSerializer<MemoryAction> {
    override val descriptor = PrimitiveSerialDescriptor("MemoryAction", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): MemoryAction {
        return when (val value = decoder.decodeString().lowercase()) {
            "add" -> MemoryAction.ADD
            "update" -> MemoryAction.UPDATE
            "delete" -> MemoryAction.DELETE
            "none" -> MemoryAction.NONE
            else -> throw IllegalArgumentException("Unknown action: $value")
        }
    }

    override fun serialize(encoder: Encoder, value: MemoryAction) {
        encoder.encodeString(value.name.lowercase())
    }
}

/**
 * 单条冲突解决决策
 */
@Serializable
@LLMDescription("记忆冲突解决决策")
data class MemoryDecision(
    @property:LLMDescription("操作类型: ADD/UPDATE/DELETE/NONE")
    val action: MemoryAction,
    @property:LLMDescription("新提取的事实（ADD/UPDATE 时必填）")
    val newFact: ExtractedFact? = null,
    @property:LLMDescription("已有事实的ID（UPDATE/DELETE 时必填）")
    val existingFactId: Int? = null,
    @property:LLMDescription("决策原因")
    val reason: String = ""
)

/**
 * 冲突解决结果
 */
@Serializable
@LLMDescription("冲突解决结果")
data class ConflictResolutionResult(
    @property:LLMDescription("所有决策列表")
    val decisions: List<MemoryDecision>
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
