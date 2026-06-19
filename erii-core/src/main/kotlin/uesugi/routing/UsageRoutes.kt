package uesugi.routing

import io.ktor.server.auth.*
import io.ktor.server.jte.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.koin.ktor.ext.inject
import uesugi.core.component.usage.TokenUsageRepository
import uesugi.core.component.usage.TokenUsageSummary

private val json = Json { encodeDefaults = true }

fun buildUsageViewModel(
    summary: TokenUsageSummary,
    botId: String? = null,
    botName: String? = null,
    groupId: String? = null,
    groupName: String? = null,
    basePath: String = ""
): UsageViewModel = UsageViewModel(
    botId = botId,
    botName = botName,
    groupId = groupId,
    groupName = groupName,
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
    dailySeriesJson = json.encodeToString(summary.dailySeries),
    basePath = basePath
)

fun Routing.configureUsageRoutes() {
    authenticate("basic") {
        get("/usage") {
            val tokenUsageRepository by inject<TokenUsageRepository>()

            val botId = call.request.queryParameters["botId"]
            val groupId = call.request.queryParameters["groupId"]
            val botName = call.request.queryParameters["botName"]
            val groupName = call.request.queryParameters["groupName"]

            val summary = tokenUsageRepository.summary(botId = botId, groupId = groupId)
            val viewModel = buildUsageViewModel(
                summary = summary,
                botId = botId,
                botName = botName,
                groupId = groupId,
                groupName = groupName
            )

            call.respond(JteContent("usage-template.kte", mapOf("vm" to viewModel)))
        }
    }
}
