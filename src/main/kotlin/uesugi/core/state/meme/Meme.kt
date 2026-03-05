package uesugi.core.state.meme

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import uesugi.core.message.resource.ResourceRecord
import uesugi.toolkit.JSON

/**
 * 表情包表 - 存储群聊中的表情包及其描述
 *
 * 分析逻辑：
 * 1. 收集：扫描图片消息，用md5去重，累计出现3次开始分析
 * 2. 提取任务：分析描述、用途、标签，生成向量嵌入
 * 3. 分析后继续累计，第6、12、24次...（翻倍）时重新分析，覆盖之前结果
 */
object MemeTable : IntIdTable("meme") {
    const val DEFAULT_LENGTH = 255

    val botMark = varchar("bot_mark", length = DEFAULT_LENGTH)
    val groupId = varchar("group_id", length = DEFAULT_LENGTH)

    val resourceId = integer("resource_id")
    val md5 = varchar("md5", length = DEFAULT_LENGTH)

    // 收集的上下文列表（JSON数组格式，最大400条）
    val contexts = text("contexts").default("[]")

    // 累计出现次数
    val seenCount = integer("seen_count").default(1)

    // 上次分析的计数（用于判断是否需要重新分析）
    val lastAnalyzedCount = integer("last_analyzed_count").default(0)

    // LLM分析结果
    val description = text("description").nullable()
    val purpose = text("purpose").nullable()
    val tags = text("tags").nullable()

    // 向量ID
    val vectorId = varchar("vector_id", length = DEFAULT_LENGTH).nullable()

    // 使用统计
    val usageCount = integer("usage_count").default(0)
    val lastUsedAt = datetime("last_used_at").nullable()

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
}

/**
 * 表情包扫描状态表 - 记录每个 bot + group 最后扫描的 history id
 */
object MemoScanStateTable : IntIdTable("meme_scan_state") {
    const val DEFAULT_LENGTH = 255

    val botMark = varchar("bot_mark", length = DEFAULT_LENGTH)
    val groupId = varchar("group_id", length = DEFAULT_LENGTH)

    // 最后扫描的 history id
    val lastHistoryId = integer("last_history_id").default(0)

    // 最后扫描时间
    val lastScanAt = datetime("last_scan_at").defaultExpression(CurrentDateTime)
}

/**
 * 表情包实体
 */
class MemeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MemeEntity>(MemeTable)

    var botMark by MemeTable.botMark
    var groupId by MemeTable.groupId
    var resourceId by MemeTable.resourceId
    var md5 by MemeTable.md5

    var contexts by MemeTable.contexts
    var seenCount by MemeTable.seenCount

    var lastAnalyzedCount by MemeTable.lastAnalyzedCount
    var description by MemeTable.description
    var purpose by MemeTable.purpose
    var tags by MemeTable.tags
    var vectorId by MemeTable.vectorId

    var usageCount by MemeTable.usageCount
    var lastUsedAt by MemeTable.lastUsedAt

    var createdAt by MemeTable.createdAt
    var updatedAt by MemeTable.updatedAt
}

/**
 * 扫描状态实体
 */
class MemeScanStateEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<MemeScanStateEntity>(MemoScanStateTable)

    var botMark by MemoScanStateTable.botMark
    var groupId by MemoScanStateTable.groupId
    var lastHistoryId by MemoScanStateTable.lastHistoryId
    var lastScanAt by MemoScanStateTable.lastScanAt
}

@Serializable
data class MemeRecord(
    val id: Int? = null,
    val botId: String,
    val groupId: String,
    val resourceId: Int,
    val resource: ResourceRecord? = null,
    val md5: String,
    val contexts: List<String> = emptyList(),
    val seenCount: Int = 1,
    val lastAnalyzedCount: Int = 0,
    val description: String? = null,
    val purpose: String? = null,
    val tags: String? = null,
    val vectorId: String? = null,
    val usageCount: Int = 0,
    val lastUsedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

fun MemeEntity.toRecord(): MemeRecord {
    return MemeRecord(
        id = id.value,
        botId = botMark,
        groupId = groupId,
        resourceId = resourceId,
        md5 = md5,
        contexts = try {
            JSON.decodeFromString<List<String>>(contexts)
        } catch (_: Exception) {
            emptyList()
        },
        seenCount = seenCount,
        lastAnalyzedCount = lastAnalyzedCount,
        description = description,
        purpose = purpose,
        tags = tags,
        vectorId = vectorId,
        usageCount = usageCount,
        lastUsedAt = lastUsedAt,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

@Serializable
data class MemeScanStateRecord(
    val id: Int? = null,
    val botMark: String,
    val groupId: String,
    val lastHistoryId: Int = 0,
    val lastScanAt: LocalDateTime
)

fun MemeScanStateEntity.toRecord(): MemeScanStateRecord {
    return MemeScanStateRecord(
        id = id.value,
        botMark = botMark,
        groupId = groupId,
        lastHistoryId = lastHistoryId,
        lastScanAt = lastScanAt
    )
}

data class MemeResource(
    val id: Int,
    val botId: String,
    val groupId: String,
    val resourceId: Int,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MemeResource) return false

        if (id != other.id) return false
        if (resourceId != other.resourceId) return false
        if (botId != other.botId) return false
        if (groupId != other.groupId) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + resourceId
        result = 31 * result + botId.hashCode()
        result = 31 * result + groupId.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
