package plugins.steamwatcher

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.koin.core.context.GlobalContext
import uesugi.toolkit.JSON
import uesugi.toolkit.logger


object SteamApi {

    val client by GlobalContext.get().inject<HttpClient>()

    private val log = logger()

    suspend fun getPlayerSummary(steamId: String): PlayerSummary? {
        val apiKey = SteamWatcherConfig.apiKey.takeIf { it.isNotBlank() } ?: return null
        val url = "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v2/?key=$apiKey&steamids=$steamId"
        return executeRequest(url)
    }

    suspend fun getPlayerAchievements(steamId: String, appId: String): List<Achievement>? {
        val apiKey = SteamWatcherConfig.apiKey.takeIf { it.isNotBlank() } ?: return null
        val url =
            "https://api.steampowered.com/ISteamUserStats/GetPlayerAchievements/v1/?key=$apiKey&steamid=$steamId&appid=$appId"
        return executeRequest<AchievementResponse>(url)?.playerstats?.achievements
    }

    suspend fun getSchemaForGame(appId: String): GameSchema? {
        val apiKey = SteamWatcherConfig.apiKey.takeIf { it.isNotBlank() } ?: return null
        val langParam = if (SteamWatcherConfig.enableTranslation) "&l=${SteamWatcherConfig.language}" else ""
        val url = "https://api.steampowered.com/ISteamUserStats/GetSchemaForGame/v2/?key=$apiKey&appid=$appId$langParam"
        return executeRequest(url)
    }

    suspend fun getStoreGameName(appId: String): String? {
        // 这个API不需要API Key，但需要语言参数
        val lang = if (SteamWatcherConfig.enableTranslation) SteamWatcherConfig.language else "english"
        val url = "https://store.steampowered.com/api/appdetails?appids=$appId&l=$lang"
        try {
            val body = client.get(url).bodyAsText()
            val data = JSON.decodeFromString<JsonObject>(body)
            val appData = data[appId]?.jsonObject ?: return null
            val success = appData["success"]?.jsonPrimitive?.booleanOrNull ?: false
            if (!success) return null

            // 返回 data.name 字段
            return appData["data"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
        } catch (e: Exception) {
            log.warn("Steam Store API request failed for URL $url: ${e.message}")
            return null
        }
    }

    // 获取全局成就解锁率
    suspend fun getGlobalAchievementPercentages(appId: String): List<GlobalAchievement>? {
        SteamWatcherConfig.apiKey.takeIf { it.isNotBlank() } ?: return null
        val url = "https://api.steampowered.com/ISteamUserStats/GetGlobalAchievementPercentagesForApp/v2/?gameid=$appId"
        return executeRequest<GlobalAchievementResponse>(url)?.achievementpercentages?.achievements
    }

    private suspend inline fun <reified T> executeRequest(url: String): T? {
        try {
            val body = client.get(url).bodyAsText()
            if (T::class == PlayerSummary::class) {
                val data = JSON.decodeFromString<PlayerResponse>(body)
                return data.response.players.firstOrNull() as? T
            }
            return JSON.decodeFromString<T>(body)
        } catch (e: Exception) {
            log.warn("Steam API request failed for URL $url: ${e.message}")
            return null
        }
    }

    // 数据类
    @Serializable
    data class PlayerResponse(val response: PlayerList)

    @Serializable
    data class PlayerList(val players: List<PlayerSummary>)

    @Serializable
    data class PlayerSummary(
        val steamid: String,
        val personaname: String,
        val profileurl: String,
        val avatarfull: String,
        val personastate: Int,
        val gameextrainfo: String? = null,
        val gameid: String? = null
    )

    @Serializable
    data class AchievementResponse(val playerstats: PlayerStats)

    @Serializable
    data class PlayerStats(val achievements: List<Achievement> = emptyList())

    @Serializable
    data class Achievement(val apiname: String, val achieved: Int, val unlocktime: Long)

    @Serializable
    data class GameSchema(val game: GameInfo)

    @Serializable
    data class GameInfo(
        val gameName: String? = null,
        val availableGameStats: AvailableGameStats? = null
    )

    @Serializable
    data class AvailableGameStats(val achievements: List<SchemaAchievement>)

    @Serializable
    data class SchemaAchievement(
        val name: String,
        val displayName: String,
        val description: String? = null,
        val icon: String
    )

    @Serializable
    data class GlobalAchievementResponse(val achievementpercentages: GlobalAchievementList)

    @Serializable
    data class GlobalAchievementList(val achievements: List<GlobalAchievement>)

    @Serializable
    data class GlobalAchievement(val name: String, val percent: Double)
}