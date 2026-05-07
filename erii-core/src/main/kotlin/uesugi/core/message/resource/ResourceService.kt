package uesugi.core.message.resource

import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.data.*

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

    fun getAllResourcesByGroup(botMark: String, groupId: String, limit: Int = 500): List<ResourceRecord> {
        return transaction {
            ResourceEntity.find {
                (ResourceTable.botMark eq botMark) and (ResourceTable.groupId eq groupId)
            }
                .orderBy(ResourceTable.createdAt to SortOrder.DESC)
                .limit(limit)
                .toList()
                .map { it.toRecord() }
        }
    }

    fun deleteResource(id: Int): Boolean {
        return transaction {
            val entity = ResourceEntity.findById(id) ?: return@transaction false
            val stillReferenced = HistoryEntity.find { HistoryTable.resourceId eq id }.any()
            if (stillReferenced) {
                return@transaction false
            }
            entity.delete()
            true
        }
    }

}