package uesugi.core

import ai.koog.agents.core.tools.reflect.ToolSet
import uesugi.DEBUG_GROUP_ID
import uesugi.core.ProactiveSpeakFeature.NONE
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
data class ProactiveSpeakEvent(
    val botId: String,
    private val _groupId: String,
    val impulse: Double = 0.0,
    val interruptionMode: InterruptionMode,
    val input: String? = null,
    val chatPointRule: String? = null,
    val toolSets: ((ChatToolSet) -> ToolSet)? = null,
    val flag: ProactiveSpeakFeatureFlag = NONE,
    val echo: String = Uuid.random().toHexString(),
) {
    val groupId: String
        get() = DEBUG_GROUP_ID ?: _groupId
}

enum class InterruptionMode {
    Interrupt, Icebreak, Routine
}

object ProactiveSpeakFeature {
    const val NONE = 0x0
    const val IGNORE_INTERRUPT = 0x1
    const val GRAB = 0x2
    const val FALLBACK = 0x4
    const val CHAT_URGENT = 0x8
}

typealias ProactiveSpeakFeatureFlag = Int

infix fun ProactiveSpeakFeatureFlag?.has(flag: ProactiveSpeakFeatureFlag): Boolean {
    return this?.let { it and flag != 0 } ?: false
}