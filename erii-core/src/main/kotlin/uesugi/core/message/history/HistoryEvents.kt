package uesugi.core.message.history

import uesugi.common.data.HistoryRecord

data class HistorySavedEvent(
    val isAtBot: Boolean,
    val historyRecord: HistoryRecord
)