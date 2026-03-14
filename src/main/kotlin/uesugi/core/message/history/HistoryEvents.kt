package uesugi.core.message.history

data class HistorySavedEvent(
    val isAtBot: Boolean,
    val historyRecord: HistoryRecord
)