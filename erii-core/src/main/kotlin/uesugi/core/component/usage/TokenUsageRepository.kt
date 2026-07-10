package uesugi.core.component.usage

import ai.koog.prompt.Prompt
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.*
import kotlinx.serialization.json.*
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.LLMModelChoice
import uesugi.common.toolkit.ConfigHolder
import kotlin.math.round
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class TokenPricing(
    val inputCacheHitPerMillion: Double,
    val inputCacheMissPerMillion: Double,
    val outputPerMillion: Double,
    val unit: String
)

internal object TokenUsageCacheHitKeys {
    val PREFERRED_NAMES = setOf(
        "cached_tokens",
        "cache_read_input_tokens",
        "cached_input_tokens",
        "input_cached_tokens",
        "prompt_cache_hit_tokens",
        "cache_hit_tokens",
        "cache_read_tokens",
        "cached_content_token_count",
        "cache_hit"
    )

    fun matchesPreferred(key: String): Boolean =
        normalizeKey(key) in PREFERRED_NAMES

    fun matchesFallback(key: String): Boolean {
        val normalized = normalizeKey(key)
        return "cache" in normalized &&
                ("hit" in normalized || "read" in normalized || "cached" in normalized || "content" in normalized)
    }

    private fun normalizeKey(key: String): String =
        key.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .replace('-', '_')
            .lowercase()
}

class TokenUsageRepository {

    private val logger = KotlinLogging.logger {}

    fun record(prompt: Prompt, model: LLModel, response: Message.Assistant) {
        val meta = response.metaInfo
        val inputTokens = meta.inputTokensCount?.toLong() ?: return
        val outputTokens = meta.outputTokensCount?.toLong() ?: 0L
        val totalTokens = meta.totalTokensCount?.toLong() ?: (inputTokens + outputTokens)
        val combinedMetadata = JsonObject(
            (meta.metadata ?: JsonObject(emptyMap())).toMap() + (response.rawResponse ?: JsonObject(emptyMap())).toMap()
        )
        val cacheHitTokens = extractCacheHitTokens(combinedMetadata).coerceIn(0, inputTokens)
        val cacheMissTokens = (inputTokens - cacheHitTokens).coerceAtLeast(0)
        val tier = resolveTier(model)
        val pricing = pricingFor(tier)
        val cost = cost(
            cacheHitTokens = cacheHitTokens,
            cacheMissTokens = cacheMissTokens,
            outputTokens = outputTokens,
            pricing = pricing
        )

        val usageIdentity = UsageContext.current()

        transaction {
            TokenUsageEntity.new {
                promptId = prompt.id
                scene = prompt.id
                botId = usageIdentity?.botId
                groupId = usageIdentity?.groupId
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

    fun summary(botId: String? = null, groupId: String? = null): TokenUsageSummary {
        val records = transaction {
            val conditions = listOfNotNull(
                botId?.let { TokenUsageTable.botId eq it },
                groupId?.let { TokenUsageTable.groupId eq it }
            )
            if (conditions.isNotEmpty()) {
                TokenUsageEntity.find { conditions.reduce { a, b -> a and b } }.map { it.toRecord() }
            } else {
                TokenUsageEntity.all().map { it.toRecord() }
            }
        }
        val today = todayKey()
        val todayRecords = records.filter { it.createdAt.date.toString() == today }
        val unit = configString("llm.usage-pricing.price-unit")
            ?: (todayRecords.firstOrNull() ?: records.lastOrNull())?.priceUnit
            ?: "USD"

        fun List<TokenUsageRecord>.cacheHit() = sumOf { it.inputCacheHitTokens }
        fun List<TokenUsageRecord>.cacheMiss() = sumOf { it.inputCacheMissTokens }
        fun List<TokenUsageRecord>.output() = sumOf { it.outputTokens }
        fun List<TokenUsageRecord>.cost() = sumOf { it.cost }

        val todayInput = todayRecords.cacheHit() + todayRecords.cacheMiss()
        val cacheHitRate = if (todayInput == 0L) 0.0 else todayRecords.cacheHit().toDouble() / todayInput * 100.0

        val knownScenes = setOf(
            "插件",
            "路由",
            "聊天",
            "搜索",
            "情绪",
            "心流",
            "冲动",
            "偏好",
            "记忆",
            "摘要",
            "热词",
            "表情",
            "其他"
        )
        val knownTiers = setOf("Lite", "Flash", "Pro")
        val tierOrder = mapOf("lite" to 0, "flash" to 1, "pro" to 2)

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
            sceneBars = todayRecords.groupBars { resolveScene(it.scene) }.fillKeys(knownScenes),
            modelBars = todayRecords.groupBars { displayTier(it.tier) }
                .fillKeys(knownTiers)
                .sortedBy { tierOrder[it.name.lowercase()] ?: 3 },
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
                .fillDailyGaps()
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

    private fun List<TokenUsageChartPoint>.fillKeys(keys: Set<String>): List<TokenUsageChartPoint> {
        val existing = associateBy { it.name }
        val zeros = keys.asSequence()
            .filterNot { it in existing }
            .sorted()
            .map { TokenUsageChartPoint(it, 0, 0, 0) }
        return this + zeros
    }

    private fun List<DailyTokenUsagePoint>.fillDailyGaps(): List<DailyTokenUsagePoint> {
        if (isEmpty()) return this
        val existing = associateBy { it.date }
        val start = LocalDate.parse(first().date)
        val end = LocalDate.parse(last().date)
        val result = mutableListOf<DailyTokenUsagePoint>()
        var date = start
        while (date <= end) {
            val key = date.toString()
            result.add(existing[key] ?: DailyTokenUsagePoint(key, 0, 0.0))
            date = date.plus(DatePeriod(days = 1))
        }
        return result
    }

    private fun normalizePromptId(promptId: String): String =
        promptId.removeSurrounding("__")

    private fun resolveScene(scene: String): String = sceneFor(scene)

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

    private fun resolveTier(model: LLModel): String = when {
        model.matchesConfiguredTier { LLMModelChoice.Lite } -> "lite"
        model.matchesConfiguredTier { LLMModelChoice.Flash } -> "flash"
        model.matchesConfiguredTier { LLMModelChoice.Pro } -> "pro"
        else -> model.id
    }

    private fun LLModel.matchesConfiguredTier(tier: () -> LLModel): Boolean =
        runCatching { this == tier() }.getOrDefault(false)

    private fun displayTier(tier: String): String = tier.replaceFirstChar { it.uppercase() }

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
        runCatching { ConfigHolder.getString(path) }.getOrNull()

    private fun configDouble(path: String): Double? =
        configString(path)?.toDoubleOrNull()

    private fun sceneFor(promptId: String): String {
        if (!promptId.startsWith("__") || !promptId.endsWith("__")) return "其他"
        val id = normalizePromptId(promptId)
        if (id.startsWith("plugin_")) return "插件"
        return when (id) {
            "route" -> "路由"
            "bot_chat" -> "聊天"
            "search_analysis" -> "搜索"
            "emotion_analysis" -> "情绪"
            "flow_analysis" -> "心流"
            "volition_analysis" -> "冲动"
            "memory_user_profile" -> "偏好"
            "memory_fact_extract",
            "memory_conflict_resolve" -> "记忆"

            "memory_summary" -> "摘要"
            "evolution_slang_extract" -> "热词"
            "meme_analysis" -> "表情"
            else -> "其他"
        }
    }

    private fun extractCacheHitTokens(metadata: JsonObject): Long {
        val exact = findLong(metadata, TokenUsageCacheHitKeys::matchesPreferred)
        if (exact != null) return exact

        val fallback = findLong(metadata, TokenUsageCacheHitKeys::matchesFallback)
        if (fallback != null) {
            logger.debug { "Detected cache hit tokens via fallback: $fallback" }
            return fallback
        }

        logger.debug { "No cache hit tokens found; metadata keys: ${collectKeys(metadata)}" }
        return 0L
    }

    private fun collectKeys(element: JsonElement): List<String> = when (element) {
        is JsonObject -> element.entries.flatMap { (key, value) -> listOf(key) + collectKeys(value) }
        is JsonArray -> element.flatMap { collectKeys(it) }
        else -> emptyList()
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
