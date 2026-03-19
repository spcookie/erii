package uesugi.core.state.emotion

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.ColumnTransformer
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.notInList
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.json.json
import uesugi.common.EmotionalTendencies
import uesugi.common.JSON
import uesugi.common.PAD
import uesugi.common.rowMapMapper

object EmotionTable : IntIdTable("chat_emotion") {
    const val DEFAULT_LENGTH = 64

    val botMark = varchar("bot_mark", length = DEFAULT_LENGTH)
    val groupId = varchar("group_id", length = DEFAULT_LENGTH)
    val emotionalTendency =
        enumerationByName<EmotionalTendencies>("emotional_tendency", length = 32)
    val stimulus = varchar("stimulus", length = 255)
    val emotion = varchar("emotion", length = 255)
    val mood = varchar("mood", length = 255)
    val behavior = json<BehaviorProfile>("behavior", JSON)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val historyMessageProcessed = integer("history_message_processed")

}

class EmotionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EmotionEntity>(EmotionTable)

    var botMark by EmotionTable.botMark
    var groupId by EmotionTable.groupId
    var emotionalTendency by EmotionTable.emotionalTendency
    var stimulus: PAD by EmotionTable.stimulus.memoizedTransform(PADColumnTransformer())
    var emotion by EmotionTable.emotion.memoizedTransform(PADColumnTransformer())
    var mood by EmotionTable.mood.memoizedTransform(PADColumnTransformer())
    var behavior by EmotionTable.behavior
    var updatedAt by EmotionTable.updatedAt
    var createdAt by EmotionTable.createdAt
    var historyMessageProcessed by EmotionTable.historyMessageProcessed
}

private class PADColumnTransformer : ColumnTransformer<String, PAD> {
    override fun unwrap(value: PAD): String {
        return "${value.p},${value.a},${value.d}"
    }

    override fun wrap(value: String): PAD {
        val dimensions = value.split(",").map { s -> s.trim().toDouble() }
        return if (dimensions.size >= 3) {
            PAD(p = dimensions[0], a = dimensions[1], d = dimensions[2])
        } else {
            throw IllegalArgumentException("Invalid stimulus format: $value")
        }
    }

}


typealias Stimulus = PAD
typealias Emotion = PAD
typealias Mood = PAD

enum class Decay(val decay: Double) {
    HIGH(0.85),
    MEDIUM(0.7),
    LOW(0.5)
}


@Serializable
data class BehaviorProfile(
    val emotion: EmotionalTendencies,
    val tone: Tone,
    val aggressiveness: Aggressiveness,
    val emojiLevel: EmojiLevel
)

enum class Tone {
    FRIENDLY,
    GENTLE,
    NEUTRAL,
    IRONIC,
    LOW_ENERGY
}

enum class Aggressiveness {
    NONE,
    ABSTRACT_SARCASM,
    TEASING
}

enum class EmojiLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH
}

fun EmotionEntity.Companion.findRequiredAnalysisHistoryGroupIds(botMark: String): List<String> {
    val result = transaction {
        exec(
            """
            SELECT t1.GROUP_ID, t1.MESSAGE_COUNT
            FROM (SELECT ch.GROUP_ID                                                     AS GROUP_ID,
                         COUNT(CASE
                                   WHEN ce.HISTORY_MESSAGE_PROCESSED IS NULL THEN 1
                                   WHEN ch.ID > ce.HISTORY_MESSAGE_PROCESSED THEN 1 END) AS MESSAGE_COUNT
                  FROM chat_history ch
                           LEFT JOIN (SELECT GROUP_ID, MAX(HISTORY_MESSAGE_PROCESSED) AS HISTORY_MESSAGE_PROCESSED
                                      FROM chat_emotion
                                      GROUP BY GROUP_ID) ce
                                     ON ch.GROUP_ID = ce.GROUP_ID
                  WHERE ch.BOT_MARK = '$botMark'
                  GROUP BY ch.GROUP_ID) AS t1
            WHERE t1.MESSAGE_COUNT > 10
            """.trimIndent()
        ) { rs -> rs.rowMapMapper() } ?: emptyList()
    }
    return result.map {
        it["GROUP_ID"] as String
    }
}

fun EmotionEntity.Companion.findNotAnalysisHistoryGroupIds(
    botMark: String,
    groupIds: List<String>
): List<String> {
    return transaction {
        EmotionTable.select(EmotionTable.groupId)
            .where {
                (EmotionTable.botMark eq botMark).apply {
                    if (groupIds.isNotEmpty()) {
                        this and (EmotionTable.groupId notInList groupIds)
                    }
                }
            }.withDistinct(true)
            .map {
                it[EmotionTable.groupId]
            }
    }
}