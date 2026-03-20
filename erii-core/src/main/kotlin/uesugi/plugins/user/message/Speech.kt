package uesugi.plugins.user.message

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.google.auto.service.AutoService
import uesugi.spi.AgentExtension
import uesugi.spi.PassiveExtension

@AutoService(AgentExtension::class)
class Speech : PassiveExtension {


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
