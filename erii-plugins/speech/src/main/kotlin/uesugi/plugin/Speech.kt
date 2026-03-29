package uesugi.plugin

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.pf4j.Extension
import uesugi.spi.*

@PluginDefinition
class Speech : AgentPlugin()

@Extension
class SpeechExtension : AgentExtension<Speech> {
    companion object {
        private val log = KotlinLogging.logger {}
        private const val T2A_API_URL = "https://api.minimaxi.com/v1/t2a_v2"
        private const val DEFAULT_MODEL = "speech-2.8-hd"

        // 语言到音色ID的映射
        private val VOICE_MAP = mapOf(
            Language.CHINESE to "Chinese (Mandarin)_ExplorativeGirl",
            Language.ENGLISH to "English_PlayfulGirl",
            Language.JAPANESE to "Japanese_DecisivePrincess"
        )
    }

    enum class Language {
        CHINESE,   // 中文
        ENGLISH,   // 英文
        JAPANESE  // 日语
    }

    override fun onLoad(context: PluginContext) {
        val httpClient = context.http

        context.tool {
            {
                object : MetaToolSet {
                    @Tool
                    @LLMDescription(
                        """
                        发送语音。当需要发送语音消息或者需要“说”时使用此工具。
                        """
                    )
                    suspend fun sendSpeech(
                        @LLMDescription(
                            """
                            要转换为语音的文本内容，长度不超过10000字符
                            段落切换用换行符标记
                            支持停顿控制：支持自定义文本之间的语音时间间隔，以实现自定义文本语音停顿时间的效果。
                            使用方式：在文本中增加<#x#>标记，x 为停顿时长（单位：秒），范围 [0.01, 99.99]，最多保留两位小数。
                            文本间隔时间需设置在两个可以语音发音的文本之间，不可连续使用多个停顿标记。
                            支持的语气词标签：(laughs)笑声、(chuckle)轻笑、(coughs)咳嗽、(clear-throat)清嗓子、
                            (groans)呻吟、(breath)换气、(pant)喘气、(inhale)吸气、(exhale)呼气、(gasps)倒吸气、
                            (sniffs)吸鼻子、(sighs)叹气、(snorts)喷鼻息、(burps)打嗝、(lip-smacking)咂嘴、
                            (humming)哼唱、(hissing)嘶嘶声、(emm)嗯、(sneezes)喷嚏。
                            也可以在文本中使用<#x#>标记控制停顿间隔，x为秒数。
                            """
                        )
                        text: String,
                        @LLMDescription("语言：chinese(中文)、english(英文)、japanese(日语)，默认为chinese")
                        language: Language = Language.CHINESE,
                        @LLMDescription("语速，0.5-2.0，默认为1.0")
                        speed: Float = 1.0f,
                        @LLMDescription("情绪：happy(高兴)、sad(悲伤)、angry(愤怒)、fearful(害怕)、disgusted(厌恶)、surprised(惊讶)、calm(中性)，默认为calm")
                        emotion: String = "calm"
                    ): String {
                        return try {
                            val apiKey = config().getString("minimax-api-key")
                            if (apiKey.isBlank()) {
                                return "语音合成失败：未配置 minimax-api-key"
                            }


                            val voiceId = VOICE_MAP[language] ?: VOICE_MAP[Language.CHINESE]!!

                            log.info { "Sending speech: text=${text.take(50)}, language=$language, voiceId=$voiceId, speed=$speed, emotion=$emotion" }

                            val audioData = synthesizeSpeech(
                                httpClient,
                                apiKey,
                                text,
                                voiceId,
                                speed,
                                emotion
                            )

                            if (audioData == null) {
                                return "语音合成失败：未能获取音频数据"
                            }

                            log.info { "Speech synthesized successfully, audio size: ${audioData.size} bytes" }

                            // 尝试发送语音
                            val bool = sendVoice(MetaToolSet.meta, audioData)
                            return if (bool) {
                                "语音合成成功，已发送"
                            } else {
                                "语音合成成功，但发送失败"
                            }
                        } catch (e: Exception) {
                            log.error(e) { "Speech synthesis failed" }
                            "语音合成失败：${e.message}"
                        }
                    }
                }
            }
        }
    }

    private suspend fun synthesizeSpeech(
        httpClient: HttpClient,
        apiKey: String,
        text: String,
        voiceId: String,
        speed: Float,
        emotion: String
    ): ByteArray? {
        return withContext(Dispatchers.IO) {
            val request = T2aV2Request(
                model = DEFAULT_MODEL,
                text = text,
                stream = false,
                voiceSetting = VoiceSetting(
                    voiceId = voiceId,
                    speed = speed,
                    vol = 1.0f,
                    pitch = 0,
                    emotion = emotion
                ),
                audioSetting = AudioSetting(
                    sampleRate = 32000,
                    bitrate = 128000,
                    format = "mp3",
                    channel = 1
                ),
                outputFormat = "hex"
            )

            val response = httpClient.post(T2A_API_URL) {
                contentType(ContentType.Application.Json)
                header("Authorization", "Bearer $apiKey")
                setBody(request)
            }

            val result = response.body<T2aV2Response>()

            if (result.baseResp?.statusCode != 0) {
                log.error { "T2A API error: ${result.baseResp?.statusMsg}" }
                return@withContext null
            }

            val hexAudio = result.data?.audio
            if (hexAudio.isNullOrBlank()) {
                log.error { "T2A API returned no audio data" }
                return@withContext null
            }

            // 将 hex 转换为字节数组
            hexToBytes(hexAudio)
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        return hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }

    @Suppress("UNUSED_PARAMETER")
    private suspend fun sendVoice(meta: Meta, audioData: ByteArray): Boolean {
        return try {
            val group = meta.getGroup()

            val audio = audioData.inputStream()
                .toExternalResource()
                .use {
                    group.uploadAudio(it)
                }

            group.sendMessage(audio)

            log.info { "Voice send success, audio data size: ${audioData.size}" }
            true
        } catch (e: Exception) {
            log.error(e) { "Failed to send voice" }
            false
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class T2aV2Request(
    @field:JsonProperty("model") val model: String,
    @field:JsonProperty("text") val text: String,
    @field:JsonProperty("stream") val stream: Boolean = false,
    @field:JsonProperty("voice_setting") val voiceSetting: VoiceSetting,
    @field:JsonProperty("audio_setting") val audioSetting: AudioSetting? = null,
    @field:JsonProperty("output_format") val outputFormat: String = "hex"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VoiceSetting(
    @field:JsonProperty("voice_id") val voiceId: String,
    @field:JsonProperty("speed") val speed: Float = 1.0f,
    @field:JsonProperty("vol") val vol: Float = 1.0f,
    @field:JsonProperty("pitch") val pitch: Int = 0,
    @field:JsonProperty("emotion") val emotion: String = "happy"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AudioSetting(
    @field:JsonProperty("sample_rate") val sampleRate: Int = 32000,
    @field:JsonProperty("bitrate") val bitrate: Int = 128000,
    @field:JsonProperty("format") val format: String = "mp3",
    @field:JsonProperty("channel") val channel: Int = 1
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class T2aV2Response(
    @field:JsonProperty("data") val data: AudioData? = null,
    @field:JsonProperty("trace_id") val traceId: String? = null,
    @field:JsonProperty("extra_info") val extraInfo: ExtraInfo? = null,
    @field:JsonProperty("base_resp") val baseResp: BaseResp? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AudioData(
    @field:JsonProperty("audio") val audio: String? = null,
    @field:JsonProperty("subtitle_file") val subtitleFile: String? = null,
    @field:JsonProperty("status") val status: Int = 0
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ExtraInfo(
    @field:JsonProperty("audio_length") val audioLength: Long = 0,
    @field:JsonProperty("audio_sample_rate") val audioSampleRate: Long = 0,
    @field:JsonProperty("audio_size") val audioSize: Long = 0,
    @field:JsonProperty("bitrate") val bitrate: Long = 0,
    @field:JsonProperty("audio_format") val audioFormat: String? = null,
    @field:JsonProperty("audio_channel") val audioChannel: Long = 0,
    @field:JsonProperty("usage_characters") val usageCharacters: Long = 0
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BaseResp(
    @field:JsonProperty("status_code") val statusCode: Int = 0,
    @field:JsonProperty("status_msg") val statusMsg: String? = null
)
