package uesugi.core

import ai.koog.agents.core.tools.reflect.ToolSet
import uesugi.core.ProactiveSpeakFeature.NONE

data class ProactiveSpeakEvent(
    val botMark: String,
    val groupId: String,
    val impulse: Double = 0.0,
    val interruptionMode: InterruptionMode,
    val input: String? = null,
    val chatPointRule: String? = null,
    val toolSets: ToolSet? = null,
    val flag: ProactiveSpeakFeatureFlag = NONE,
)

enum class InterruptionMode {
    Interrupt,
    Icebreak,
    Routine
}

object ProactiveSpeakFeature {
    const val NONE = 0x0
    const val IGNORE_INTERRUPT = 0x1
    const val GRAB = 0x2
    const val FALLBACK = 0x4
}

typealias ProactiveSpeakFeatureFlag = Int

infix fun ProactiveSpeakFeatureFlag?.has(flag: ProactiveSpeakFeatureFlag): Boolean {
    return this?.let { it and flag != 0 } ?: false
}