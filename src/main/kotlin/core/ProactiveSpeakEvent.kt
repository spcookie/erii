package uesugi.core

import ai.koog.agents.core.tools.reflect.ToolSet

data class ProactiveSpeakEvent(
    val botMark: String,
    val groupId: String,
    val impulse: Double = 0.0,
    val interruptionMode: InterruptionMode,
    val input: String? = null,
    val chatPointRule: String? = null,
    val toolSets: ToolSet? = null,
)

enum class InterruptionMode {
    Interrupt,
    Icebreak,
    Routine
}