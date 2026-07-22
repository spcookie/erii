package uesugi.core.component.usage

import kotlinx.datetime.*
import kotlinx.datetime.TimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.toolkit.JSON
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

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

    @Test
    fun `summary exposes effective configured pricing without usage records`() {
        withUsageDatabase {
            val configuredStrings = mapOf(
                "llm.usage-pricing.price-unit" to "CNY"
            )
            val configuredDoubles = mapOf(
                "llm.usage-pricing.lite.input-cache-hit" to 0.02,
                "llm.usage-pricing.flash.input-cache-miss" to 0.2,
                "llm.usage-pricing.pro.output" to 12.5
            )
            val repository = TokenUsageRepository(configuredStrings::get, configuredDoubles::get)

            val summary = repository.summary(botId = "missing-bot", groupId = "missing-group")
            val pricing = assertNotNull(summary.pricing)

            assertEquals("CNY", summary.priceUnit)
            assertEquals(0.02, pricing.lite.inputCacheHit)
            assertEquals(0.075, pricing.lite.inputCacheMiss)
            assertEquals(0.30, pricing.lite.output)
            assertEquals(0.025, pricing.flash.inputCacheHit)
            assertEquals(0.2, pricing.flash.inputCacheMiss)
            assertEquals(0.40, pricing.flash.output)
            assertEquals(0.3125, pricing.pro.inputCacheHit)
            assertEquals(1.25, pricing.pro.inputCacheMiss)
            assertEquals(12.5, pricing.pro.output)

            val payload = JSON.encodeToString(summary)
            assertTrue(payload.contains("\"pricing\""))
            assertTrue(payload.contains("\"inputCacheHit\":0.02"))
            assertTrue(payload.contains("\"inputCacheMiss\":0.2"))
            assertTrue(payload.contains("\"output\":12.5"))
        }
    }

    @Test
    fun `usage scope does not change current pricing`() {
        withUsageDatabase {
            insertUsage(botId = "bot-a", groupId = "group-1", cacheMiss = 100, output = 20, cost = 0.12)
            val configuredStrings = mapOf(
                "llm.usage-pricing.price-unit" to "CNY"
            )
            val configuredDoubles = mapOf(
                "llm.usage-pricing.lite.input-cache-hit" to 0.01
            )
            val repository = TokenUsageRepository(configuredStrings::get, configuredDoubles::get)

            assertEquals(repository.summary().pricing, repository.summary("bot-a", "group-1").pricing)
            assertEquals(repository.summary().pricing, repository.summary("missing", "missing").pricing)
        }
    }

    @OptIn(ExperimentalTime::class)
    @Test
    fun `summary exposes only populated days from the last seven calendar days`() {
        withUsageDatabase {
            val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
            val twoDaysAgo = today.minus(DatePeriod(days = 2))
            val eightDaysAgo = today.minus(DatePeriod(days = 8))

            insertUsage(
                botId = "bot-a",
                groupId = "group-1",
                cacheHit = 80,
                cacheMiss = 20,
                output = 10,
                cost = 0.11,
                scene = "__bot_chat__",
                tier = "flash",
                createdAt = LocalDateTime(today, LocalTime(12, 0))
            )
            insertUsage(
                botId = "bot-a",
                groupId = "group-1",
                cacheHit = 30,
                cacheMiss = 70,
                output = 25,
                cost = 0.22,
                scene = "__search_analysis__",
                tier = "pro",
                createdAt = LocalDateTime(twoDaysAgo, LocalTime(12, 0))
            )
            insertUsage(
                botId = "bot-b",
                groupId = "group-1",
                cacheMiss = 999,
                output = 1,
                cost = 1.0,
                createdAt = LocalDateTime(today.minus(DatePeriod(days = 1)), LocalTime(12, 0))
            )
            insertUsage(
                botId = "bot-a",
                groupId = "group-1",
                cacheMiss = 500,
                output = 50,
                cost = 0.5,
                createdAt = LocalDateTime(eightDaysAgo, LocalTime(12, 0))
            )

            val summary = TokenUsageRepository().summary(botId = "bot-a", groupId = "group-1")

            assertEquals(listOf(today.toString(), twoDaysAgo.toString()), summary.dailyViews.map { it.date })
            assertEquals(80, summary.dailyViews[0].cacheHitInput)
            assertEquals(20, summary.dailyViews[0].cacheMissInput)
            assertEquals(10, summary.dailyViews[0].output)
            assertEquals(80.0, summary.dailyViews[0].cacheHitRate)
            assertEquals(30, summary.dailyViews[1].cacheHitInput)
            assertEquals(70, summary.dailyViews[1].cacheMissInput)
            assertEquals(25, summary.dailyViews[1].output)
            assertEquals(30.0, summary.dailyViews[1].cacheHitRate)
            assertEquals(125, summary.dailyViews[1].sceneBars.single { it.name == "搜索" }.let {
                it.cacheHitInput + it.cacheMissInput + it.output
            })
            assertEquals(125, summary.dailyViews[1].modelBars.single { it.name == "Pro" }.let {
                it.cacheHitInput + it.cacheMissInput + it.output
            })
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
        cacheHit: Long = 0,
        cacheMiss: Long,
        output: Long,
        cost: Double,
        scene: String = "__bot_chat__",
        tier: String = "flash",
        createdAt: LocalDateTime? = null
    ) {
        transaction {
            val record = TokenUsageEntity.new {
                promptId = scene
                this.scene = scene
                this.tier = tier
                modelId = "test-model"
                provider = "test-provider"
                this.botId = botId
                this.groupId = groupId
                inputCacheHitTokens = cacheHit
                inputCacheMissTokens = cacheMiss
                outputTokens = output
                totalTokens = cacheHit + cacheMiss + output
                inputCacheHitPricePerMillion = 0.0
                inputCacheMissPricePerMillion = 0.0
                outputPricePerMillion = 0.0
                priceUnit = "USD"
                this.cost = cost
                if (createdAt != null) {
                    this.createdAt = createdAt
                }
            }
            assertNotNull(record.id.value)
        }
    }
}
