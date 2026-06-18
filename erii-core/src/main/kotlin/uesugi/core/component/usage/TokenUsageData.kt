package uesugi.core.component.usage

import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime

object TokenUsageTable : IntIdTable("llm_token_usage") {
    const val DEFAULT_LENGTH = 128

    val promptId = varchar("prompt_id", DEFAULT_LENGTH)
    val scene = varchar("scene", DEFAULT_LENGTH)
    val tier = varchar("tier", 32)
    val modelId = varchar("model_id", DEFAULT_LENGTH)
    val provider = varchar("provider", DEFAULT_LENGTH)
    val inputCacheHitTokens = long("input_cache_hit_tokens").default(0)
    val inputCacheMissTokens = long("input_cache_miss_tokens").default(0)
    val outputTokens = long("output_tokens").default(0)
    val totalTokens = long("total_tokens").default(0)
    val inputCacheHitPricePerMillion = double("input_cache_hit_price_per_million").default(0.0)
    val inputCacheMissPricePerMillion = double("input_cache_miss_price_per_million").default(0.0)
    val outputPricePerMillion = double("output_price_per_million").default(0.0)
    val priceUnit = varchar("price_unit", 16).default("USD")
    val cost = double("cost").default(0.0)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

class TokenUsageEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TokenUsageEntity>(TokenUsageTable)

    var promptId by TokenUsageTable.promptId
    var scene by TokenUsageTable.scene
    var tier by TokenUsageTable.tier
    var modelId by TokenUsageTable.modelId
    var provider by TokenUsageTable.provider
    var inputCacheHitTokens by TokenUsageTable.inputCacheHitTokens
    var inputCacheMissTokens by TokenUsageTable.inputCacheMissTokens
    var outputTokens by TokenUsageTable.outputTokens
    var totalTokens by TokenUsageTable.totalTokens
    var inputCacheHitPricePerMillion by TokenUsageTable.inputCacheHitPricePerMillion
    var inputCacheMissPricePerMillion by TokenUsageTable.inputCacheMissPricePerMillion
    var outputPricePerMillion by TokenUsageTable.outputPricePerMillion
    var priceUnit by TokenUsageTable.priceUnit
    var cost by TokenUsageTable.cost
    var createdAt by TokenUsageTable.createdAt
}

data class TokenUsageRecord(
    val id: Int,
    val promptId: String,
    val scene: String,
    val tier: String,
    val modelId: String,
    val provider: String,
    val inputCacheHitTokens: Long,
    val inputCacheMissTokens: Long,
    val outputTokens: Long,
    val totalTokens: Long,
    val inputCacheHitPricePerMillion: Double,
    val inputCacheMissPricePerMillion: Double,
    val outputPricePerMillion: Double,
    val priceUnit: String,
    val cost: Double,
    val createdAt: LocalDateTime
)

fun TokenUsageEntity.toRecord(): TokenUsageRecord = TokenUsageRecord(
    id = id.value,
    promptId = promptId,
    scene = scene,
    tier = tier,
    modelId = modelId,
    provider = provider,
    inputCacheHitTokens = inputCacheHitTokens,
    inputCacheMissTokens = inputCacheMissTokens,
    outputTokens = outputTokens,
    totalTokens = totalTokens,
    inputCacheHitPricePerMillion = inputCacheHitPricePerMillion,
    inputCacheMissPricePerMillion = inputCacheMissPricePerMillion,
    outputPricePerMillion = outputPricePerMillion,
    priceUnit = priceUnit,
    cost = cost,
    createdAt = createdAt
)

@Serializable
data class TokenUsageChartPoint(
    val name: String,
    val cacheHitInput: Long,
    val cacheMissInput: Long,
    val output: Long
)

@Serializable
data class DailyTokenUsagePoint(
    val date: String,
    val tokens: Long,
    val cost: Double
)

@Serializable
data class TokenUsageSummary(
    val todayCacheHitInput: Long,
    val todayCacheMissInput: Long,
    val todayOutput: Long,
    val todayCost: Double,
    val priceUnit: String,
    val totalCacheHitInput: Long,
    val totalCacheMissInput: Long,
    val totalOutput: Long,
    val totalCost: Double,
    val todayCacheHitRate: Double,
    val sceneBars: List<TokenUsageChartPoint>,
    val modelBars: List<TokenUsageChartPoint>,
    val dailySeries: List<DailyTokenUsagePoint>
)
