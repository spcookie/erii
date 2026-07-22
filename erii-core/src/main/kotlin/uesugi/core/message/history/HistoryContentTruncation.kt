package uesugi.core.message.history

import uesugi.common.data.HistoryRecord

internal fun String.truncateHistoryContent(maxLength: Int): String {
    val length = maxLength.coerceAtLeast(0)
    return if (this.length > length) take(length) + "..." else this
}

internal fun String?.orEmptyTruncatedHistoryContent(maxLength: Int): String =
    this?.truncateHistoryContent(maxLength) ?: ""

internal fun HistoryRecord.truncateContent(maxLength: Int): HistoryRecord =
    content?.let { copy(content = it.truncateHistoryContent(maxLength)) } ?: this
