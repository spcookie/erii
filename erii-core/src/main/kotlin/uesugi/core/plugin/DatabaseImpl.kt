package uesugi.core.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import okio.buffer
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.HistoryRecord
import uesugi.common.HistoryTable
import uesugi.common.ref
import uesugi.core.component.ObjectStorage
import uesugi.core.message.resource.ResourceRecord
import uesugi.core.message.resource.ResourceTable
import uesugi.spi.Database

internal class DatabaseImpl : Database {
    override suspend fun getHistory(query: () -> Query): List<HistoryRecord> {
        val storage by ref<ObjectStorage>()
        return withContext(Dispatchers.IO) {
            transaction {
                query().map {
                    HistoryRecord(
                        id = it[HistoryTable.id].value,
                        botMark = it[HistoryTable.botMark],
                        groupId = it[HistoryTable.groupId],
                        userId = it[HistoryTable.userId],
                        nick = it[HistoryTable.nick],
                        messageType = it[HistoryTable.messageType],
                        content = it[HistoryTable.content],
                        resource = if (it.getOrNull(ResourceTable.id) != null) {
                            val url = it[ResourceTable.url]
                            val bytes = storage.get(url.toPath()).use { source -> source.buffer().readByteArray() }
                            ResourceRecord(
                                id = it[ResourceTable.id].value,
                                botMark = it[ResourceTable.botMark],
                                groupId = it[ResourceTable.groupId],
                                url = url,
                                fileName = it[ResourceTable.fileName],
                                size = it[ResourceTable.size],
                                md5 = it[ResourceTable.md5],
                                createdAt = it[ResourceTable.createdAt],
                                bytes = bytes
                            )
                        } else {
                            null
                        },
                        createdAt = it[HistoryTable.createdAt]
                    )
                }
            }
        }

    }

}
