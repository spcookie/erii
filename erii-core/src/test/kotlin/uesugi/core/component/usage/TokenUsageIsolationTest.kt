package uesugi.core.component.usage

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class TokenUsageIsolationTest {
    @Test
    fun `usage table stores bot and group ids`() {
        assertEquals("bot_id", TokenUsageTable.botId.name)
        assertEquals("group_id", TokenUsageTable.groupId.name)
    }

    @Test
    fun `summary filters records by bot and group`() {
        withUsageDatabase {
            insertUsage(botId = "bot-a", groupId = "group-1", cacheMiss = 100, output = 20, cost = 0.12)
            insertUsage(botId = "bot-a", groupId = "group-2", cacheMiss = 300, output = 40, cost = 0.34)
            insertUsage(botId = "bot-b", groupId = "group-1", cacheMiss = 500, output = 60, cost = 0.56)
            insertUsage(botId = null, groupId = null, cacheMiss = 700, output = 80, cost = 0.78)

            val summary = TokenUsageRepository().summary(botId = "bot-a", groupId = "group-1")

            assertEquals(100, summary.totalCacheMissInput)
            assertEquals(20, summary.totalOutput)
            assertEquals(0.12, summary.totalCost)
            assertEquals(120, summary.dailySeries.single().tokens)
        }
    }

    private fun withUsageDatabase(block: () -> Unit) {
        val database = Database.connect(
            url = "jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )
        TransactionManager.defaultDatabase = database
        transaction(database) {
            SchemaUtils.create(TokenUsageTable)
        }
        block()
    }

    private fun insertUsage(
        botId: String?,
        groupId: String?,
        cacheMiss: Long,
        output: Long,
        cost: Double
    ) {
        transaction {
            val record = TokenUsageEntity.new {
                promptId = "__bot_chat__"
                scene = "__bot_chat__"
                tier = "flash"
                modelId = "test-model"
                provider = "test-provider"
                this.botId = botId
                this.groupId = groupId
                inputCacheHitTokens = 0
                inputCacheMissTokens = cacheMiss
                outputTokens = output
                totalTokens = cacheMiss + output
                inputCacheHitPricePerMillion = 0.0
                inputCacheMissPricePerMillion = 0.0
                outputPricePerMillion = 0.0
                priceUnit = "USD"
                this.cost = cost
            }
            assertNotNull(record.id.value)
        }
    }
}
