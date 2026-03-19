package uesugi.common

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class ResourceRecord(
    val id: Int? = null,
    val botMark: String,
    val groupId: String,
    val url: String,
    val fileName: String,
    val size: Long,
    val md5: String,
    val histories: List<HistoryRecord>? = null,
    val createdAt: LocalDateTime,
    @Transient var bytes: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResourceRecord) return false

        if (id != other.id) return false
        if (size != other.size) return false
        if (botMark != other.botMark) return false
        if (groupId != other.groupId) return false
        if (url != other.url) return false
        if (fileName != other.fileName) return false
        if (md5 != other.md5) return false
        if (histories != other.histories) return false
        if (createdAt != other.createdAt) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id ?: 0
        result = 31 * result + size.hashCode()
        result = 31 * result + botMark.hashCode()
        result = 31 * result + groupId.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + md5.hashCode()
        result = 31 * result + (histories?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + (bytes?.contentHashCode() ?: 0)
        return result
    }
}