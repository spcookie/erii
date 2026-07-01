package uesugi.core.message.resource

import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.data.ResourceEntity
import uesugi.common.data.ResourceRecord
import uesugi.common.data.ResourceTable
import uesugi.common.data.toRecord

class ResourceService {

    fun saveResource(resource: ResourceRecord): ResourceRecord {
        return transaction {
            ResourceEntity.new {
                botMark = resource.botMark
                groupId = resource.groupId
                url = resource.url
                fileName = resource.fileName
                size = resource.size
                md5 = resource.md5
                createdAt = resource.createdAt
            }.toRecord()
        }
    }

    fun getResource(id: Int): ResourceRecord? {
        return transaction {
            ResourceEntity.findById(id)?.toRecord()
        }
    }

    fun getAllResourcesByGroup(
        botMark: String,
        groupId: String,
        offset: Int = 0,
        limit: Int = 500
    ): Pair<List<ResourceRecord>, Int> {
        return transaction {
            val condition =
                (ResourceTable.botMark eq botMark) and (ResourceTable.groupId eq groupId)
            val baseQuery = ResourceEntity.find { condition }
            val total = baseQuery.count().toInt()
            val query = ResourceTable
                .selectAll()
                .where { condition }
                .orderBy(ResourceTable.createdAt to SortOrder.DESC)
            val pageQuery = if (limit > 0) {
                query.limit(limit).offset(offset.toLong())
            } else {
                query.offset(offset.toLong())
            }
            val items = ResourceEntity.wrapRows(pageQuery)
                .map { it.toRecord() }
            items to total
        }
    }

    fun deleteResource(id: Int): Boolean {
        return transaction {
            val entity = ResourceEntity.findById(id) ?: return@transaction false
            entity.delete()
            true
        }
    }

    fun findResourcesOlderThan(cutoff: LocalDateTime, limit: Int = 100): List<ResourceRecord> {
        return transaction {
            ResourceEntity.find { ResourceTable.createdAt less cutoff }
                .orderBy(ResourceTable.createdAt to SortOrder.ASC)
                .limit(limit)
                .map { it.toRecord() }
        }
    }

}
