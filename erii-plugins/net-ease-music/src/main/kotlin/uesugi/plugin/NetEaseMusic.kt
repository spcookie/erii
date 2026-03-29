package uesugi.plugin

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.pf4j.Extension
import uesugi.common.logger
import uesugi.spi.*
import uesugi.spi.MetaToolSet.Companion.meta

/**
 * 网易云音乐插件
 */
@PluginDefinition
class NetEaseMusic : AgentPlugin()

@Extension
class NetEaseMusicExtension : PassiveExtension<NetEaseMusic> {

    private val log = logger()

    companion object {
        private lateinit var MUSIC_API_BASE: String
        private lateinit var NAPCAT_HTTP_URL: String
        private lateinit var NAPCAT_TOKEN: String
    }

    override fun onLoad(context: PluginContext) {
        log.info("Loading NetEase Music plugin...")

        requireNotNull(
            context.config()
                .tryGetString("api-base")
                ?.also { MUSIC_API_BASE = it }
        ) {
            "api-base is required for NetEase Music plugin"
        }

        val napcat = requireNotNull(
            context.config().getConfig("napcat")
        ) {
            "napcat is required for NetEase Music plugin"
        }

        requireNotNull(
            napcat.tryGetString("http-url")
                ?.also { NAPCAT_HTTP_URL = it }
        ) {
            "Napcat http-url is required for Napcat plugin"
        }

        requireNotNull(
            napcat.tryGetString("token")
                ?.also { NAPCAT_TOKEN = it }
        ) {
            "Napcat token is required for Napcat plugin"
        }

        context.tool { { ToolSet(context) } }

        log.info("NetEase Music plugin loaded")
    }

    class ToolSet(val context: PluginContext) : MetaToolSet {

        private val log = logger()

        @Tool
        @LLMDescription("当用户想要搜索或点播音乐时，调用此 tool 搜索音乐并发送音乐")
        suspend fun searchMusic(keyword: String, limit: Int = 5): String {
            log.info("Search music keyword: {}", keyword)
            if (keyword.isBlank()) {
                return "未提取到关键词"
            }

            var limit = limit
            if (limit > 5) limit = 5

            val musicCards = context.search(keyword, limit)
            log.info("Found {} music cards for keyword: {}", musicCards.size, keyword)

            return sendMusicCards(musicCards)
        }

        /**
         * 搜索音乐并返回音乐卡片列表
         */
        private suspend fun PluginContext.search(keyword: String, limit: Int = 5): List<MusicCardResult> {
            return withContext(Dispatchers.IO) {
                try {
                    val response = http.get("$MUSIC_API_BASE/search") {
                        parameter("keywords", keyword)
                        parameter("limit", limit)
                        parameter("type", 1)
                    }

                    val result = response.body<MusicSearchResult>()

                    val songs = result.result?.songs
                    if (songs.isNullOrEmpty()) {
                        return@withContext emptyList()
                    }

                    // 为每首歌曲创建音乐卡片
                    songs.map { song ->
                        val musicUrl = getMusicUrl(song.id)
                        MusicCardResult.fromSong(song, musicUrl)
                    }
                } catch (e: Exception) {
                    log.error("搜索音乐失败: {}", e.message, e)
                    emptyList()
                }
            }
        }

        /**
         * 根据歌曲ID获取音乐URL
         */
        private suspend fun PluginContext.getMusicUrl(songId: Long): String? {
            return withContext(Dispatchers.IO) {
                try {
                    val response = http.get("$MUSIC_API_BASE/song/url") {
                        parameter("id", songId)
                    }

                    val result = response.body<MusicUrlResult>()
                    result.data?.firstOrNull()?.url
                } catch (e: Exception) {
                    log.error("获取音乐URL失败: {}", e.message, e)
                    null
                }
            }
        }

        private suspend fun sendMusicCards(musicCards: List<MusicCardResult>): String {
            for (cardResult in musicCards) {
                try {
                    context.http.post("$NAPCAT_HTTP_URL/send_msg") {
                        bearerAuth("hG8dQqGk6jGC")
                        contentType(ContentType.Application.Json)
                        setBody(
                            mapOf(
                                "message_type" to "group",
                                "group_id" to meta.groupId,
                                "message" to listOf(
                                    mapOf(
                                        "type" to "music",
                                        "data" to mapOf(
                                            "type" to "163",
                                            "id" to cardResult.id
                                        )
                                    )
                                )
                            )
                        )
                    }
                } catch (e: Exception) {
                    log.error("发送音乐卡片失败: {}", e.message, e)
                    return "发送音乐卡片失败: ${e.message}"
                }
            }
            return "已发送音乐卡片"
        }
    }

    override fun onUnload() {
        log.info("NetEase Music plugin unloaded")
    }

}

/**
 * 音乐搜索结果
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MusicSearchResult(
    @field:JsonProperty("result") val result: SearchResult? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchResult(
    @field:JsonProperty("songs") val songs: List<Song>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Song(
    @field:JsonProperty("id") val id: Long = 0,
    @field:JsonProperty("name") val name: String = "",
    @field:JsonProperty("ar") val artists: List<Artist>? = null,
    @field:JsonProperty("al") val album: Album? = null,
    @field:JsonProperty("dt") val duration: Long = 0
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Artist(
    @field:JsonProperty("name") val name: String = "",
    @field:JsonProperty("id") val id: Long? = null,
    @field:JsonProperty("tns") val tns: List<String>? = null,
    @field:JsonProperty("alias") val alias: List<String>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Album(
    @field:JsonProperty("name") val name: String = "",
    @field:JsonProperty("picUrl") val picUrl: String = "",
    @field:JsonProperty("id") val id: Long? = null,
    @field:JsonProperty("pic") val pic: Long? = null,
    @field:JsonProperty("pic_str") val picStr: String? = null,
    @field:JsonProperty("tns") val tns: List<String>? = null,
    @field:JsonProperty("publishTime") val publishTime: Long? = null
)

/**
 * 音乐卡片结果 - 用于生成网易云音乐卡片消息
 */
data class MusicCardResult(
    val id: Long,
    /** 消息卡片标题. */
    val title: String,
    /** 消息卡片内容. */
    val summary: String,
    /** 点击卡片跳转网页 URL. */
    val jumpUrl: String,
    /** 消息卡片图片 URL. */
    val pictureUrl: String,
    /** 音乐文件 URL. */
    val musicUrl: String,
    /** 在消息列表显示. */
    val brief: String
) {
    companion object {
        private const val NETEASE_BASE_URL = "https://music.163.com"

        /**
         * 从 Song 对象创建 MusicCardResult
         */
        fun fromSong(song: Song, musicUrl: String? = null): MusicCardResult {
            val artists = song.artists?.joinToString("/") { it.name } ?: "未知歌手"
            val musicUrlFinal = musicUrl ?: "$NETEASE_BASE_URL/song/media/outer/url?id=${song.id}"

            return MusicCardResult(
                id = song.id,
                title = song.name,
                summary = artists,
                jumpUrl = "$NETEASE_BASE_URL/song/${song.id}/",
                pictureUrl = song.album?.picUrl ?: "",
                musicUrl = musicUrlFinal,
                brief = "[分享]${song.name}"
            )
        }
    }
}

/**
 * 音乐URL查询响应
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class MusicUrlResult(
    @field:JsonProperty("code") val code: Int = 0,
    @field:JsonProperty("data") val data: List<MusicUrlData>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MusicUrlData(
    @field:JsonProperty("id") val id: Long = 0,
    @field:JsonProperty("url") val url: String? = null
)
