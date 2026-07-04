package uesugi.core.state.meme

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.data.HistoryEntity
import uesugi.common.data.HistoryTable
import uesugi.common.data.MessageType
import uesugi.common.data.ResourceEntity
import uesugi.common.data.ResourceTable
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class MemeRepositoryTest {
    @Test
    fun `recent image messages skip image history without resource`() {
        val database = createDatabase()
        transaction(database) {
            HistoryEntity.new {
                botMark = "bot-a"
                groupId = "group-a"
                userId = "user-a"
                nick = "user"
                messageType = MessageType.IMAGE
                content = "image without stored resource"
            }
            val resource = ResourceEntity.new {
                botMark = "bot-a"
                groupId = "group-a"
                url = "images/ok.png"
                fileName = "ok.png"
                size = 42
                md5 = "md5-ok"
            }
            HistoryEntity.new {
                botMark = "bot-a"
                groupId = "group-a"
                userId = "user-a"
                nick = "user"
                messageType = MessageType.IMAGE
                content = "image with resource"
                this.resource = resource
            }
        }

        val images = MemeRepository().getRecentImageMessages("bot-a", "group-a")

        assertEquals(1, images.size)
        assertEquals("image with resource", images.single().content)
        assertEquals("md5-ok", images.single().md5)
    }

    private fun createDatabase(): Database {
        val database = Database.connect(
            url = "jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        TransactionManager.defaultDatabase = database
        transaction(database) {
            SchemaUtils.create(ResourceTable, HistoryTable)
        }
        return database
    }
}
