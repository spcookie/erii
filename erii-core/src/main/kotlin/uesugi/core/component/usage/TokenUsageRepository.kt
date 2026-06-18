package uesugi.core.component.usage

import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.LLMProviderChoice
import uesugi.server.SystemConfigHolder
import kotlin.math.round
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class TokenPricing(
    val inputCacheHitPerMillion: Double,
    val inputCacheMissPerMillion: Double,
    val outputPerMillion: Double,
    val unit: String
)

class TokenUsageRepository {

    fun record(prompt: Prompt, model: LLModel, response: Message.Assistant) {
        val meta = response.metaInfo
        val inputTokens = meta.inputTokensCount?.toLong() ?: return
        val outputTokens = meta.outputTokensCount?.toLong() ?: 0L
        val totalTokens = meta.totalTokensCount?.toLong() ?: inputTokens + outputTokens
        val cacheHitTokens = extractCacheHitTokens(meta.metadata ?: JsonObject(emptyMap())).coerceIn(0, inputTokens)
        val cacheMissTokens = (inputTokens - cacheHitTokens).coerceAtLeast(0)
        val tier = resolveTier(model)
        val pricing = pricingFor(tier)
        val cost = cost(
            cacheHitTokens = cacheHitTokens,
            cacheMissTokens = cacheMissTokens,
            outputTokens = outputTokens,
            pricing = pricing
        )

        transaction {
            TokenUsageEntity.new {
                promptId = prompt.id
                scene = sceneFor(prompt.id)
                this.tier = tier
                modelId = meta.modelId ?: model.id
                provider = model.provider.id
                inputCacheHitTokens = cacheHitTokens
                inputCacheMissTokens = cacheMissTokens
                this.outputTokens = outputTokens
                this.totalTokens = totalTokens
                inputCacheHitPricePerMillion = pricing.inputCacheHitPerMillion
                inputCacheMissPricePerMillion = pricing.inputCacheMissPerMillion
                outputPricePerMillion = pricing.outputPerMillion
                priceUnit = pricing.unit
                this.cost = cost
            }
        }
    }

    fun summary(): TokenUsageSummary {
        val records = transaction {
            TokenUsageEntity.all().map { it.toRecord() }
        }
        val today = todayKey()
        val todayRecords = records.filter { it.createdAt.date.toString() == today }
        val unit = (todayRecords.firstOrNull() ?: records.lastOrNull())?.priceUnit ?: "USD"

        fun List<TokenUsageRecord>.cacheHit() = sumOf { it.inputCacheHitTokens }
        fun List<TokenUsageRecord>.cacheMiss() = sumOf { it.inputCacheMissTokens }
        fun List<TokenUsageRecord>.output() = sumOf { it.outputTokens }
        fun List<TokenUsageRecord>.cost() = sumOf { it.cost }

        val todayInput = todayRecords.cacheHit() + todayRecords.cacheMiss()
        val cacheHitRate = if (todayInput == 0L) 0.0 else todayRecords.cacheHit().toDouble() / todayInput * 100.0

        return TokenUsageSummary(
            todayCacheHitInput = todayRecords.cacheHit(),
            todayCacheMissInput = todayRecords.cacheMiss(),
            todayOutput = todayRecords.output(),
            todayCost = roundMoney(todayRecords.cost()),
            priceUnit = unit,
            totalCacheHitInput = records.cacheHit(),
            totalCacheMissInput = records.cacheMiss(),
            totalOutput = records.output(),
            totalCost = roundMoney(records.cost()),
            todayCacheHitRate = round(cacheHitRate * 100) / 100,
            sceneBars = todayRecords.groupBars { it.scene },
            modelBars = todayRecords.groupBars { displayModel(it) },
            dailySeries = records
                .groupBy { it.createdAt.date.toString() }
                .map { (date, items) ->
                    DailyTokenUsagePoint(
                        date = date,
                        tokens = items.sumOf { it.inputCacheHitTokens + it.inputCacheMissTokens + it.outputTokens },
                        cost = roundMoney(items.sumOf { it.cost })
                    )
                }
                .sortedBy { it.date }
        )
    }

    private fun List<TokenUsageRecord>.groupBars(key: (TokenUsageRecord) -> String): List<TokenUsageChartPoint> =
        groupBy(key)
            .map { (name, items) ->
                TokenUsageChartPoint(
                    name = name,
                    cacheHitInput = items.sumOf { it.inputCacheHitTokens },
                    cacheMissInput = items.sumOf { it.inputCacheMissTokens },
                    output = items.sumOf { it.outputTokens }
                )
            }
            .sortedByDescending { it.cacheHitInput + it.cacheMissInput + it.output }

    private fun displayModel(record: TokenUsageRecord): String =
        "${record.tier} / ${record.modelId}"

    private fun cost(
        cacheHitTokens: Long,
        cacheMissTokens: Long,
        outputTokens: Long,
        pricing: TokenPricing
    ): Double {
        return cacheHitTokens * pricing.inputCacheHitPerMillion / 1_000_000.0 +
                cacheMissTokens * pricing.inputCacheMissPerMillion / 1_000_000.0 +
                outputTokens * pricing.outputPerMillion / 1_000_000.0
    }

    private fun roundMoney(value: Double): Double = round(value * 1_000_000) / 1_000_000

    @OptIn(ExperimentalTime::class)
    private fun todayKey(): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()

    private fun resolveTier(model: LLModel): String = when (model) {
        LLMProviderChoice.Lite -> "lite"
        LLMProviderChoice.Flash -> "flash"
        LLMProviderChoice.Pro -> "pro"
        else -> model.id
    }

    private fun pricingFor(tier: String): TokenPricing {
        val root = "llm.usage-pricing"
        val unit = configString("$root.price-unit") ?: "USD"
        return TokenPricing(
            inputCacheHitPerMillion = configDouble("$root.$tier.input-cache-hit")
                ?: defaultPricing[tier]?.inputCacheHitPerMillion ?: 0.0,
            inputCacheMissPerMillion = configDouble("$root.$tier.input-cache-miss")
                ?: defaultPricing[tier]?.inputCacheMissPerMillion ?: 0.0,
            outputPerMillion = configDouble("$root.$tier.output") ?: defaultPricing[tier]?.outputPerMillion ?: 0.0,
            unit = unit
        )
    }

    private fun configString(path: String): String? =
        SystemConfigHolder.config.propertyOrNull(path)?.getString()

    private fun configDouble(path: String): Double? =
        configString(path)?.toDoubleOrNull()

    private fun sceneFor(promptId: String): String {
        if (!promptId.startsWith("__") || !promptId.endsWith("__")) return "其他"
        val id = promptId.removeSurrounding("__")
        if (id.startsWith("plugin_")) return "插件"
        return when (id) {
            "bot_chat" -> "BotAgent"
            "search_analysis" -> "搜索分析"
            "emotion_analysis",
            "flow_analysis",
            "volition_analysis",
            "memory_user_profile",
            "memory_summary",
            "memory_fact_extract",
            "memory_conflict_resolve",
            "evolution_slang_extract",
            "meme_analysis",
            "meme_query_transform" -> "状态分析"

            else -> "其他"
        }
    }

    private fun extractCacheHitTokens(metadata: JsonObject): Long {
        val preferredNames = setOf(
            "cached_tokens",
            "cache_read_input_tokens",
            "cached_input_tokens",
            "input_cached_tokens",
            "prompt_cache_hit_tokens",
            "cache_hit_tokens"
        )
        return findLong(metadata) { key -> key.lowercase() in preferredNames } ?: 0L
    }

    private fun findLong(element: JsonElement, keyMatch: (String) -> Boolean): Long? {
        return when (element) {
            is JsonObject -> {
                element.entries.firstNotNullOfOrNull { (key, value) ->
                    if (keyMatch(key)) value.jsonPrimitive.longOrNull else findLong(value, keyMatch)
                }
            }

            is JsonArray -> element.firstNotNullOfOrNull { findLong(it, keyMatch) }
            else -> null
        }
    }

    private companion object {
        val defaultPricing = mapOf(
            "lite" to TokenPricing(0.01875, 0.075, 0.30, "USD"),
            "flash" to TokenPricing(0.025, 0.10, 0.40, "USD"),
            "pro" to TokenPricing(0.3125, 1.25, 10.00, "USD")
        )
    }
}
