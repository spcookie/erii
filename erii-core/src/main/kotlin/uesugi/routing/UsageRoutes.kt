package uesugi.routing

import io.ktor.http.*
import io.ktor.server.jte.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import uesugi.core.component.usage.TokenUsageSummary
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val json = Json { encodeDefaults = true }

object UsageViewCache {
    private val cache = ConcurrentHashMap<String, TokenUsageSummary>()

    fun put(summary: TokenUsageSummary): String {
        val id = UUID.randomUUID().toString()
        cache[id] = summary
        return id
    }

    fun take(id: String): TokenUsageSummary? = cache.remove(id)
}

fun Routing.configureUsageRoutes() {
    get("/usage/{id}") {
        val id = call.parameters["id"]
        val summary = id?.let { UsageViewCache.take(it) }
        if (summary == null) {
            call.respondText("Usage view expired", ContentType.Text.Plain, HttpStatusCode.NotFound)
        } else {
            val viewModel = UsageViewModel(
                todayCacheHitInput = summary.todayCacheHitInput,
                todayCacheMissInput = summary.todayCacheMissInput,
                todayOutput = summary.todayOutput,
                todayCost = summary.todayCost,
                priceUnit = summary.priceUnit,
                totalCacheHitInput = summary.totalCacheHitInput,
                totalCacheMissInput = summary.totalCacheMissInput,
                totalOutput = summary.totalOutput,
                totalCost = summary.totalCost,
                todayCacheHitRate = summary.todayCacheHitRate,
                sceneBars = summary.sceneBars,
                modelBars = summary.modelBars,
                dailySeries = summary.dailySeries,
                sceneBarsJson = json.encodeToString(summary.sceneBars),
                modelBarsJson = json.encodeToString(summary.modelBars),
                dailySeriesJson = json.encodeToString(summary.dailySeries)
            )
            call.respond(JteContent("usage-template.kte", mapOf("vm" to viewModel)))
        }
    }
}
