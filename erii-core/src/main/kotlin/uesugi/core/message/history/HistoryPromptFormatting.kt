package uesugi.core.message.history

import kotlinx.datetime.format
import uesugi.common.data.HistoryRecord
import uesugi.common.toolkit.DateTimeFormat

internal fun HistoryRecord.asLlmPrompt(): String =
    "[ID:${id ?: 0} $userId ${createdAt.format(DateTimeFormat)}] ${content ?: ""}"
