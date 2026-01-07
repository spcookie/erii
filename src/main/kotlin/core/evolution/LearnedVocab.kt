package uesugi.core.evolution

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.datetime.datetime
import uesugi.core.history.HistoryTable.DEFAULT_LENGTH

/**
 * 学习词汇表 - 存储从群聊中学习到的流行语、梗、黑话
 *
 * 用于实现"模因进化系统"：让机器人的语言风格随群聊氛围动态漂移
 */
object LearnedVocabTable : IntIdTable("learned_vocab") {
    val botMark = varchar("bot_mark", length = DEFAULT_LENGTH)
    val groupId = varchar("group_id", length = DEFAULT_LENGTH)

    val word = varchar("word", 255)
    val type = varchar("type", 50)
    val meaning = text("meaning")
    val example = text("example")

    val weight = integer("weight").default(50)
    val lastSeen = datetime("last_seen").defaultExpression(CurrentDateTime)
    val createdAt = datetime("created_at").defaultExpression(CurrentDateTime)
}

/**
 * 学习词汇实体
 */
class LearnedVocabEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<LearnedVocabEntity>(LearnedVocabTable)

    var botMark by LearnedVocabTable.botMark
    var groupId by LearnedVocabTable.groupId
    var word by LearnedVocabTable.word
    var type by LearnedVocabTable.type
    var meaning by LearnedVocabTable.meaning
    var example by LearnedVocabTable.example
    var weight by LearnedVocabTable.weight
    var lastSeen by LearnedVocabTable.lastSeen
    var createdAt by LearnedVocabTable.createdAt
}

/**
 * 流行语/梗/黑话数据结构
 *
 * @property word 词汇本身，如"绝绝子"、"泰裤辣"
 * @property type 词性类型，如 adjective(形容词)、exclamation(感叹词)、noun(名词)
 * @property meaning 词汇的含义解释，如"太好了"
 * @property example 使用示例，如"这奶茶绝绝子"
 */
@Serializable
data class SlangWord(
    val word: String,
    val type: String,
    val meaning: String,
    val example: String
)
