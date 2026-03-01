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
import uesugi.toolkit.JSON
import uesugi.toolkit.rowMapMapper
import kotlin.math.pow
import kotlin.math.sqrt

object EmotionTable : IntIdTable("chat_emotion") {
    const val DEFAULT_LENGTH = 64

    val botMark = varchar("bot_mark", length = DEFAULT_LENGTH)
    val groupId = varchar("group_id", length = DEFAULT_LENGTH)
    val emotionalTendency =
        enumerationByName<uesugi.core.state.emotion.EmotionalTendencies>("emotional_tendency", length = 32)
    val stimulus = varchar("stimulus", length = 255)
    val emotion = varchar("emotion", length = 255)
    val mood = varchar("mood", length = 255)
    val behavior = json<uesugi.core.state.emotion.BehaviorProfile>("behavior", JSON)
    val updatedAt = datetime("updated_at").defaultExpression(CurrentDateTime)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
    val historyMessageProcessed = integer("history_message_processed")

}

class EmotionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EmotionEntity>(_root_ide_package_.uesugi.core.state.emotion.EmotionTable)

    var botMark by _root_ide_package_.uesugi.core.state.emotion.EmotionTable.botMark
    var groupId by _root_ide_package_.uesugi.core.state.emotion.EmotionTable.groupId
    var emotionalTendency by _root_ide_package_.uesugi.core.state.emotion.EmotionTable.emotionalTendency
    var stimulus: uesugi.core.state.emotion.PAD by _root_ide_package_.uesugi.core.state.emotion.EmotionTable.stimulus.memoizedTransform(
        _root_ide_package_.uesugi.core.state.emotion.PADColumnTransformer()
    )
    var emotion by _root_ide_package_.uesugi.core.state.emotion.EmotionTable.emotion.memoizedTransform(
        _root_ide_package_.uesugi.core.state.emotion.PADColumnTransformer()
    )
    var mood by _root_ide_package_.uesugi.core.state.emotion.EmotionTable.mood.memoizedTransform(_root_ide_package_.uesugi.core.state.emotion.PADColumnTransformer())
    var behavior by _root_ide_package_.uesugi.core.state.emotion.EmotionTable.behavior
    var updatedAt by _root_ide_package_.uesugi.core.state.emotion.EmotionTable.updatedAt
    var createdAt by _root_ide_package_.uesugi.core.state.emotion.EmotionTable.createdAt
    var historyMessageProcessed by _root_ide_package_.uesugi.core.state.emotion.EmotionTable.historyMessageProcessed
}

private class PADColumnTransformer : ColumnTransformer<String, uesugi.core.state.emotion.PAD> {
    override fun unwrap(value: uesugi.core.state.emotion.PAD): String {
        return "${value.p},${value.a},${value.d}"
    }

    override fun wrap(value: String): uesugi.core.state.emotion.PAD {
        val dimensions = value.split(",").map { s -> s.trim().toDouble() }
        return if (dimensions.size >= 3) {
            _root_ide_package_.uesugi.core.state.emotion.PAD(p = dimensions[0], a = dimensions[1], d = dimensions[2])
        } else {
            throw IllegalArgumentException("Invalid stimulus format: $value")
        }
    }

}

@Serializable
data class PAD(
    val p: Double,
    val a: Double,
    val d: Double
) {

    fun normalize(): PAD {
        return this.copy(
            p = p / 4.0,
            a = a / 4.0,
            d = d / 4.0
        )
    }

    operator fun plus(other: PAD): PAD {
        return PAD(this.p + other.p, this.a + other.a, this.d + other.d)
    }

    operator fun minus(other: PAD): PAD {
        return PAD(this.p - other.p, this.a - other.a, this.d - other.d)
    }

    operator fun times(scalar: Double): PAD {
        return PAD(this.p * scalar, this.a * scalar, this.d * scalar)
    }

    operator fun times(other: PAD): PAD {
        return PAD(this.p * other.p, this.a * other.a, this.d * other.d)
    }

    operator fun div(scalar: Double): PAD {
        if (scalar == 0.0) throw IllegalArgumentException("Cannot divide by zero.")
        return PAD(this.p / scalar, this.a / scalar, this.d / scalar)
    }

    operator fun div(other: PAD): PAD {
        if (other.p == 0.0 || other.a == 0.0 || other.d == 0.0) {
            throw IllegalArgumentException("Cannot perform element-wise division by a PAD object containing zero.")
        }
        return PAD(this.p / other.p, this.a / other.a, this.d / other.d)
    }

    companion object {

        val ZERO = PAD(p = 0.0, a = 0.0, d = 0.0)

        fun from(scale: uesugi.core.state.emotion.PadScale12): PAD {
            val p = (scale.q1 - scale.q4 + scale.q7 - scale.q10) / 4
            val a = (-scale.q2 + scale.q5 - scale.q8 + scale.q11) / 4
            val d = (scale.q3 - scale.q6 + scale.q9 - scale.q12) / 4
            return PAD(p, a, d)
        }

    }

}

typealias Stimulus = uesugi.core.state.emotion.PAD
typealias Emotion = uesugi.core.state.emotion.PAD
typealias Mood = uesugi.core.state.emotion.PAD

enum class Decay(val decay: Double) {
    HIGH(0.85),
    MEDIUM(0.7),
    LOW(0.5)
}

enum class EmotionalTendencies(val pad: uesugi.core.state.emotion.PAD) {
    /**
     * 喜悦
     */
    JOY(_root_ide_package_.uesugi.core.state.emotion.PAD(p = 2.77, a = 1.21, d = 1.42)),

    /**
     * 乐观
     */
    OPTIMISM(_root_ide_package_.uesugi.core.state.emotion.PAD(p = 2.48, a = 1.05, d = 1.75)),

    /**
     * 轻松
     */
    RELAXATION(_root_ide_package_.uesugi.core.state.emotion.PAD(p = 2.19, a = -0.66, d = 1.05)),

    /**
     * 惊奇
     */
    SURPRISE(_root_ide_package_.uesugi.core.state.emotion.PAD(p = 1.72, a = 1.71, d = 0.22)),

    /**
     * 温和
     */
    MILDNESS(_root_ide_package_.uesugi.core.state.emotion.PAD(p = 1.57, a = -0.79, d = 0.38)),

    /**
     * 依赖
     */
    DEPENDENCE(_root_ide_package_.uesugi.core.state.emotion.PAD(p = 0.39, a = -0.81, d = 1.48)),

    /**
     * 无聊
     */
    BOREDOM(_root_ide_package_.uesugi.core.state.emotion.PAD(p = -0.53, a = -1.25, d = -0.84)),

    /**
     * 悲伤
     */
    SADNESS(_root_ide_package_.uesugi.core.state.emotion.PAD(p = -0.89, a = 0.17, d = -0.70)),

    /**
     * 恐惧
     */
    FEAR(_root_ide_package_.uesugi.core.state.emotion.PAD(p = -0.93, a = 1.30, d = -0.64)),

    /**
     * 焦虑
     */
    ANXIETY(_root_ide_package_.uesugi.core.state.emotion.PAD(p = -0.95, a = 0.32, d = -0.63)),

    /**
     * 藐视
     */
    CONTEMPT(_root_ide_package_.uesugi.core.state.emotion.PAD(p = -1.58, a = 0.32, d = 1.02)),

    /**
     * 厌恶
     */
    DISGUST(_root_ide_package_.uesugi.core.state.emotion.PAD(p = -1.80, a = 0.40, d = 0.67)),

    /**
     * 愤懑
     */
    RESENTMENT(_root_ide_package_.uesugi.core.state.emotion.PAD(p = -1.98, a = 1.10, d = 0.60)),

    /**
     * 敌意
     */
    HOSTILITY(_root_ide_package_.uesugi.core.state.emotion.PAD(p = -2.08, a = 1.00, d = 1.12));

    companion object {

        /**
         * 计算两个PAD值之间的欧式距离
         *
         * @param pad1 第一个PAD点
         * @param pad2 第二个PAD点
         * @return 距离
         */
        private fun calculateDistance(
            pad1: uesugi.core.state.emotion.PAD,
            pad2: uesugi.core.state.emotion.PAD
        ): Double {
            val pDiff = pad1.p - pad2.p
            val aDiff = pad1.a - pad2.a
            val dDiff = pad1.d - pad2.d
            return sqrt(pDiff.pow(2) + aDiff.pow(2) + dDiff.pow(2))
        }

        /**
         * 找出与用户当前情感最接近的基本情感倾向
         *
         * @param pad 用户的PAD情感状态
         * @return 距离最小的基本情感
         */
        fun findClosest(pad: uesugi.core.state.emotion.PAD): EmotionalTendencies {
            return EmotionalTendencies.entries.toTypedArray().minByOrNull {
                calculateDistance(pad, it.pad)
            } ?: throw IllegalStateException("EmotionalTendencies enum cannot be empty.")
        }
    }

}

@Serializable
data class BehaviorProfile(
    val emotion: uesugi.core.state.emotion.EmotionalTendencies,
    val tone: uesugi.core.state.emotion.Tone,
    val aggressiveness: uesugi.core.state.emotion.Aggressiveness,
    val emojiLevel: uesugi.core.state.emotion.EmojiLevel
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

fun uesugi.core.state.emotion.EmotionEntity.Companion.findRequiredAnalysisHistoryGroupIds(botMark: String): List<String> {
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

fun uesugi.core.state.emotion.EmotionEntity.Companion.findNotAnalysisHistoryGroupIds(
    botMark: String,
    groupIds: List<String>
): List<String> {
    return transaction {
        _root_ide_package_.uesugi.core.state.emotion.EmotionTable.select(_root_ide_package_.uesugi.core.state.emotion.EmotionTable.groupId)
            .where {
                (_root_ide_package_.uesugi.core.state.emotion.EmotionTable.botMark eq botMark).apply {
                    if (groupIds.isNotEmpty()) {
                        this and (_root_ide_package_.uesugi.core.state.emotion.EmotionTable.groupId notInList groupIds)
                    }
                }
            }.withDistinct(true)
            .map {
                it[_root_ide_package_.uesugi.core.state.emotion.EmotionTable.groupId]
            }
    }
}