package uesugi.core

import uesugi.DEBUG_GROUP_ID

data class ProactiveSpeakEvent(
    val botMark: String,
    val groupId: String,
    val impulse: Double = 0.0,
    val interruptionMode: InterruptionMode,
    val input: String? = null,
    val chatPointRule: String? = null,
    val debugGroupId: String? = DEBUG_GROUP_ID
)

enum class InterruptionMode {
    Interrupt,
    Icebreak,
    Routine
}