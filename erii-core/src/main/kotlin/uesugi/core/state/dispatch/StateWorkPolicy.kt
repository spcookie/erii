package uesugi.core.state.dispatch

import uesugi.common.toolkit.StateTriggerProfile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

enum class StateWorkKind {
    EMOTION,
    FLOW,
    VOLITION,
    MEMORY,
    SUMMARY,
    MEME_COLLECT,
    MEME_ANALYZE,
    EVOLUTION
}

enum class BacklogMode {
    LATEST_WINDOW,
    SEQUENTIAL
}

data class StateWorkPolicy(
    val backlogMode: BacklogMode,
    val debounce: Duration,
    val maxWait: Duration,
    val minMessages: Int,
    val batchLimit: Int
)

val StateTriggerProfile.reconciliationInterval: Duration
    get() = when (this) {
        StateTriggerProfile.REALTIME -> 1.minutes
        StateTriggerProfile.BALANCED -> 3.minutes
        StateTriggerProfile.ECONOMY -> 5.minutes
    }

fun StateTriggerProfile.policies(): Map<StateWorkKind, StateWorkPolicy> {
    val debounce = when (this) {
        StateTriggerProfile.REALTIME -> 15.seconds
        StateTriggerProfile.BALANCED -> 30.seconds
        StateTriggerProfile.ECONOMY -> 1.minutes
    }
    val realtimeWait = when (this) {
        StateTriggerProfile.REALTIME -> 1.minutes
        StateTriggerProfile.BALANCED -> 3.minutes
        StateTriggerProfile.ECONOMY -> 5.minutes
    }
    val knowledgeWait = when (this) {
        StateTriggerProfile.REALTIME -> 5.minutes
        StateTriggerProfile.BALANCED -> 15.minutes
        StateTriggerProfile.ECONOMY -> 30.minutes
    }
    val emotionMinimum = when (this) {
        StateTriggerProfile.REALTIME -> 11
        StateTriggerProfile.BALANCED -> 20
        StateTriggerProfile.ECONOMY -> 30
    }
    val realtimeMinimum = when (this) {
        StateTriggerProfile.REALTIME -> 20
        StateTriggerProfile.BALANCED -> 30
        StateTriggerProfile.ECONOMY -> 50
    }
    val knowledgeMinimum = when (this) {
        StateTriggerProfile.REALTIME -> 30
        StateTriggerProfile.BALANCED -> 60
        StateTriggerProfile.ECONOMY -> 100
    }
    val memeDelay = when (this) {
        StateTriggerProfile.REALTIME -> 30.seconds
        StateTriggerProfile.BALANCED -> 1.minutes
        StateTriggerProfile.ECONOMY -> 2.minutes
    }
    val memeAnalysisWait = when (this) {
        StateTriggerProfile.REALTIME -> 5.minutes
        StateTriggerProfile.BALANCED -> 30.minutes
        StateTriggerProfile.ECONOMY -> 60.minutes
    }
    val evolutionMinimum = when (this) {
        StateTriggerProfile.REALTIME -> 100
        StateTriggerProfile.BALANCED -> 200
        StateTriggerProfile.ECONOMY -> 300
    }
    val evolutionWait = when (this) {
        StateTriggerProfile.REALTIME -> 15.minutes
        StateTriggerProfile.BALANCED -> 30.minutes
        StateTriggerProfile.ECONOMY -> 60.minutes
    }

    return mapOf(
        StateWorkKind.EMOTION to StateWorkPolicy(
            BacklogMode.LATEST_WINDOW,
            debounce,
            realtimeWait,
            emotionMinimum,
            200
        ),
        StateWorkKind.FLOW to StateWorkPolicy(BacklogMode.LATEST_WINDOW, debounce, realtimeWait, realtimeMinimum, 100),
        StateWorkKind.VOLITION to StateWorkPolicy(
            BacklogMode.LATEST_WINDOW,
            debounce,
            realtimeWait,
            realtimeMinimum,
            100
        ),
        StateWorkKind.MEMORY to StateWorkPolicy(BacklogMode.SEQUENTIAL, debounce, knowledgeWait, knowledgeMinimum, 400),
        StateWorkKind.SUMMARY to StateWorkPolicy(
            BacklogMode.SEQUENTIAL,
            debounce,
            knowledgeWait,
            knowledgeMinimum,
            200
        ),
        StateWorkKind.MEME_COLLECT to StateWorkPolicy(BacklogMode.SEQUENTIAL, memeDelay, memeDelay, 1, 500),
        StateWorkKind.MEME_ANALYZE to StateWorkPolicy(BacklogMode.SEQUENTIAL, debounce, memeAnalysisWait, 1, 20),
        StateWorkKind.EVOLUTION to StateWorkPolicy(
            BacklogMode.SEQUENTIAL,
            debounce,
            evolutionWait,
            evolutionMinimum,
            500
        )
    )
}
