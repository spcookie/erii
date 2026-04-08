@file:Suppress("unused")

package uesugi.routing

import uesugi.common.EmotionalTendencies
import uesugi.common.PAD
import uesugi.core.state.emotion.Aggressiveness
import uesugi.core.state.emotion.EmojiLevel
import uesugi.core.state.emotion.Tone
import uesugi.core.state.flow.FlowMeterState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min


/** 情绪映射数据 */
data class EmotionInfo(
    val name: String,
    val desc: String
)

/** 情绪映射表 */
object EmotionMap {
    private val emotions = mapOf(
        EmotionalTendencies.JOY to EmotionInfo("喜悦", "充满欢乐与活力的状态"),
        EmotionalTendencies.OPTIMISM to EmotionInfo("乐观", "对未来持积极态度"),
        EmotionalTendencies.RELAXATION to EmotionInfo("轻松", "平和舒适的放松状态"),
        EmotionalTendencies.SURPRISE to EmotionInfo("惊奇", "对意外事件的反应"),
        EmotionalTendencies.MILDNESS to EmotionInfo("温和", "温柔平静的情绪"),
        EmotionalTendencies.DEPENDENCE to EmotionInfo("依赖", "需要他人支持的状态"),
        EmotionalTendencies.BOREDOM to EmotionInfo("无聊", "缺乏兴趣与刺激"),
        EmotionalTendencies.SADNESS to EmotionInfo("悲伤", "消极低落的情绪"),
        EmotionalTendencies.FEAR to EmotionInfo("恐惧", "对威胁的警觉反应"),
        EmotionalTendencies.ANXIETY to EmotionInfo("焦虑", "不安与担忧的状态"),
        EmotionalTendencies.CONTEMPT to EmotionInfo("藐视", "轻视与不屑的态度"),
        EmotionalTendencies.DISGUST to EmotionInfo("厌恶", "强烈的排斥感"),
        EmotionalTendencies.RESENTMENT to EmotionInfo("愤懑", "压抑的愤怒与不满"),
        EmotionalTendencies.HOSTILITY to EmotionInfo("敌意", "对抗性的负面情绪")
    )

    fun get(emotion: EmotionalTendencies): EmotionInfo =
        emotions[emotion] ?: EmotionInfo(emotion.name, "未知情绪状态")
}

/** 语气映射 */
fun Tone.displayName(): String = when (this) {
    Tone.FRIENDLY -> "友好"
    Tone.GENTLE -> "温柔"
    Tone.NEUTRAL -> "中性"
    Tone.IRONIC -> "讽刺"
    Tone.LOW_ENERGY -> "低能量"
}

/** 攻击性映射 */
fun Aggressiveness.displayName(): String = when (this) {
    Aggressiveness.NONE -> "无攻击性"
    Aggressiveness.ABSTRACT_SARCASM -> "抽象讽刺"
    Aggressiveness.TEASING -> "戏弄"
}

/** 表情级别映射 */
fun EmojiLevel.displayName(): String = when (this) {
    EmojiLevel.NONE -> "无表情"
    EmojiLevel.LOW -> "少量表情"
    EmojiLevel.MEDIUM -> "适度表情"
    EmojiLevel.HIGH -> "丰富表情"
}

/** 心流状态映射 */
data class FlowStateInfo(
    val name: String,
    val color: String,
    val icon: String
)

fun FlowMeterState.info(): FlowStateInfo = when (this) {
    FlowMeterState.STANDBY -> FlowStateInfo("状态未开", "#71717a", "fa-circle")
    FlowMeterState.GETTING_BETTER -> FlowStateInfo("渐入佳境", "#3b82f6", "fa-circle-half-stroke")
    FlowMeterState.FLOW_BURST -> FlowStateInfo("心流爆发", "#ef4444", "fa-circle-dot")
}

/** PAD 归一化（-4 ~ +4 → 0 ~ 100） */
fun Double.normalizeForDisplay(): Double = max(0.0, min(100.0, (this + 4) / 8 * 100))

/** 情绪倾向判断 */
fun PAD.emotionTendency(): String = when {
    p > 1 -> "积极愉悦"
    p < -1 -> "消极低落"
    else -> "情绪平和"
}

/** 格式化当前时间 */
fun formatCurrentTime(): String {
    val formatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    return LocalDateTime.now().format(formatter)
}

/**
 * 群组状态页面 ViewModel
 */
data class GroupStatusViewModel(
    val botId: String,
    val botName: String,
    val groupId: String,
    val groupStatus: BotStatus.ByGroup,
    val pluginStats: BotStatus.PluginStats,
    val currentTime: String,
    val basePath: String = ""
)
