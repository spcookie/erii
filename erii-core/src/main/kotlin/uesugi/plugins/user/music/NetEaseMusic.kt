package uesugi.plugins.user.music

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.message.Message
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.auto.service.AutoService
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import net.mamoe.mirai.message.data.MusicKind
import net.mamoe.mirai.message.data.MusicShare
import org.koin.core.component.KoinComponent
import uesugi.common.PSFeature
import uesugi.common.logger
import uesugi.config.LLMModelsChoice
import uesugi.plugins.getGroup
import uesugi.spi.*
import uesugi.spi.EmptyConfig.plus

/**
 * 网易云音乐插件
 */
@AutoService(AgentPlugin::class)
class NetEaseMusic : RoutePlugin, ClassNameMixin, KoinComponent {

    private val log = logger()

    companion object {
        private const val MUSIC_API_BASE = "http://127.0.0.1:13000"
    }

    override fun onLoad(context: PluginContext) {
        log.info("Loading NetEase Music plugin...")

        // 处理音乐搜索请求
        context.chain { meta ->
            val input = meta.input ?: return@chain

            // 使用 LLM 提取搜索关键词
            val keyword = extractKeyword(input, llm)

            log.info("Extracted keyword: {}", keyword)

            if (keyword.isBlank()) {
                return@chain
            }

            coroutineScope {
                val deferred = async {
                    // 搜索音乐并返回音乐卡片列表
                    val musicCards = searchMusic(keyword)
                    log.info("Found {} music cards for keyword: {}", musicCards.size, keyword)
                    musicCards
                }

                val flag = atomic(false)

                suspend fun send() {
                    if (flag.compareAndSet(expect = false, update = true)) {
                        val musicCards = deferred.await()
                        for (cardResult in musicCards) {
                            meta.getGroup()
                                .sendMessage(
                                    MusicShare(
                                        MusicKind.QQMusic,
                                        cardResult.title,
                                        cardResult.summary,
                                        cardResult.jumpUrl,
                                        cardResult.pictureUrl,
                                        cardResult.musicUrl,
                                        cardResult.brief
                                    )
                                )
                        }
                    }
                }

                val toolSet = object : MetaToolSet {
                    @Tool
                    @LLMDescription("发送音乐卡片")
                    suspend fun sendMusicCards() {
                        send()
                    }
                }

                meta.sendAgent(
                    "用户要求你发送 $keyword，请使用工具发送音乐卡片",
                    Feature(PSFeature.CHAT_URGENT) + ToolSetBuilder { listOf(toolSet) }
                ) {
                    runCompletion { send() }
                    this@coroutineScope
                }
            }
        }

        log.info("NetEase Music plugin loaded")
    }

    /**
     * 使用 LLM 从输入中提取搜索关键词
     */
    private suspend fun extractKeyword(input: String, llm: PromptExecutor): String {
        return try {
            val responses = llm.execute(
                prompt("extract") {
                    system {
                        text(
                            """
                            你是一个音乐搜索关键词提取助手。
                            用户的输入可能包含口语化的表达，你需要从中提取出歌曲名或歌手名。
                            只返回提取到的关键词，不要返回其他内容。
                            如果用户只是表达想听音乐但没有具体歌曲，返回"无"。
                            """
                        )
                    }
                    user { text(input) }
                },
                LLMModelsChoice.Flash
            )
            val content = responses.filterIsInstance<Message.Assistant>().firstOrNull()?.content
            content?.trim()?.takeIf { it != "无" && it.isNotBlank() } ?: input
        } catch (e: Exception) {
            log.error("LLM 提取关键词失败: {}", e.message, e)
            // LLM 失败时使用简单提取
            simpleExtractKeyword(input)
        }
    }

    /**
     * 简单的关键词提取（LLM 失败时的后备方案）
     */
    private fun simpleExtractKeyword(input: String): String {
        // 移除常见前缀
        val keywords = listOf("听", "点歌", "播放", "搜歌", "音乐", "歌", "来一首", "一首")
        var result = input
        for (keyword in keywords) {
            result = result.removePrefix(keyword).trim()
        }
        return result.ifBlank { input }
    }

    /**
     * 搜索音乐并返回音乐卡片列表
     */
    private suspend fun PluginContext.searchMusic(keyword: String, limit: Int = 5): List<MusicCardResult> {
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

    override fun onUnload() {
        log.info("NetEase Music plugin unloaded")
    }

    override val matcher: Pair<String, String>
        get() = "MUSIC_SEARCH" to """
            当用户想要搜索或点播音乐时，选择此分类。

            判断标准（满足任一即可）：
            - 用户发送的内容包含歌曲名、歌手名或歌词片段
            - 用户明确要求播放音乐
            - 消息以 "听" 开头或包含 "点歌"、"搜歌"、"音乐"、"来一首"、"播放" 等关键词

            MUSIC_SEARCH 的消息应该进行音乐搜索并返回结果。
        """.trimIndent()
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
