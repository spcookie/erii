package uesugi.common

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.serialization.json.Json
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

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

inline fun <reified T : Any> ref(): Lazy<T> = GlobalContext.get().inject<T>()

/**
 * 根据中文打字速度计算发送延迟
 *
 * @param text 要发送的文本
 * @param cpm 字/分钟 (如 80)
 * @param jitter 抖动比例 (0.1 = ±10%)
 */
fun calcHumanTypingDelay(
    text: String,
    cpm: Int = 160,
    jitter: Double = 0.15
): Duration {
    val charCount = text.count { !it.isWhitespace() }
    val cps = cpm / 60.0

    val baseDelayMs = (charCount / cps * 1000).toLong()
    val jitterFactor = 1 + Random.nextDouble(-jitter, jitter)

    return (baseDelayMs * jitterFactor).toLong().coerceAtLeast(300).microseconds
}