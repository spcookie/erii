package uesugi.core.resource

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import uesugi.core.history.HistoryEntity
import uesugi.core.history.HistoryRecord
import uesugi.core.history.HistoryTable

object ResourceTable : IntIdTable("chat_resource") {
    const val DEFAULT_LENGTH = 255

    val botMark = varchar("bot_mark", length = DEFAULT_LENGTH)
    val groupId = varchar("group_id", length = DEFAULT_LENGTH)

    val url = varchar("url", length = 1024)

    val fileName = varchar("file_name", length = DEFAULT_LENGTH).nullable()

    val size = long("size").nullable()

    val md5 = varchar("md5", length = DEFAULT_LENGTH).nullable()

    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

class ResourceEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ResourceEntity>(ResourceTable)

    var botMark by ResourceTable.botMark
    var groupId by ResourceTable.groupId
    var url by ResourceTable.url
    var fileName by ResourceTable.fileName
    var size by ResourceTable.size
    var md5 by ResourceTable.md5
    val histories by HistoryEntity optionalReferrersOn HistoryTable.resourceId
    var createdAt by ResourceTable.createdAt

}

@Serializable
data class ResourceRecord(
    val id: Int? = null,
    val botMark: String,
    val groupId: String,
    val url: String,
    val fileName: String? = null,
    val size: Long? = null,
    val md5: String? = null,
    val histories: List<HistoryRecord>? = null,
    val createdAt: LocalDateTime
)
