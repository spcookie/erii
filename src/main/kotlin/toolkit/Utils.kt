package uesugi.toolkit

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.sql.ResultSet

inline fun <reified T : Any> T.logger() = LoggerFactory.getLogger(T::class.java)!!

fun logger(name: String) = LoggerFactory.getLogger(name)!!

val JSON = Json {
    ignoreUnknownKeys = true
}

@OptIn(FormatStringsInDatetimeFormats::class)
val DateTimeFormat = LocalDateTime.Format {
    byUnicodePattern("yyyy-MM-dd HH:mm:ss")
}

fun ResultSet.rowMapMapper(): List<Map<String, Any?>> {
    val meta = this.metaData
    val cols = (1..meta.columnCount).map { meta.getColumnLabel(it) }

    return buildList {
        while (this@rowMapMapper.next()) {
            add(
                cols.associateWith { col ->
                    this@rowMapMapper.getObject(col)
                }
            )
        }
    }
}