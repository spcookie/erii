package uesugi.core.evolution

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.core.history.HistoryEntity
import uesugi.core.history.HistoryTable
import uesugi.core.history.MessageType
import uesugi.toolkit.logger
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

/**
 * 词汇库服务 - 管理学习到的流行语、梗、黑话
 *
 * 提供增删改查和热度管理功能，实现"遗忘机制"
 */
class VocabularyService {
    companion object {
        private val log = logger()

        private const val MIN_WEIGHT_THRESHOLD = 20
        private const val ACTIVE_WEIGHT_THRESHOLD = 50
        private const val WEIGHT_DECAY_PER_DAY = 10
        private const val WEIGHT_INCREASE_ON_USE = 10
        private const val WEIGHT_DECREASE_ON_NEGATIVE = 50
        private const val DEFAULT_WEIGHT = 50

        private const val MIN_MESSAGE_LENGTH = 2
    }

    /**
     * 获取6小时前最活跃的消息
     *
     * @param botMark 机器人标识
     * @param groupId 群组ID
     * @param limit 最大消息数量，默认500条
     * @return 消息内容列表
     */
    @OptIn(ExperimentalTime::class)
    fun getMostActiveMessages(
        botMark: String,
        groupId: String,
        limit: Int = 500,
        range: Duration
    ): List<String> = transaction {
        val yesterday = Clock.System.now().minus(range).toLocalDateTime(TimeZone.currentSystemDefault())

        log.debug("开始获取${range.inWholeHours}小时前活跃消息, botId=$botMark, groupId=$groupId, limit=$limit")

        val messages = HistoryEntity.find {
            (HistoryTable.botMark eq botMark) and
                    (HistoryTable.groupId eq groupId) and
                    (HistoryTable.createdAt greaterEq yesterday) and
                    (HistoryTable.messageType eq MessageType.TEXT) and
                    (HistoryTable.userId neq botMark)
        }
            .mapNotNull { entity ->
                val content = entity.content ?: return@mapNotNull null
                if (shouldFilterMessage(content)) null else content
            }
            .take(limit)

        log.debug("获取6小时前活跃消息完成, botId=$botMark, groupId=$groupId, 消息数=${messages.size}")
        messages
    }

    /**
     * 判断消息是否应该被过滤掉
     *
     * 清洗规则：
     * 1. 剔除 URL、纯表情包、系统消息
     * 2. 剔除过短的无意义字符（如 "a", "1"）
     *
     * @param content 消息内容
     * @return true表示应该过滤，false表示保留
     */
    private fun shouldFilterMessage(content: String): Boolean {
        return content.length < MIN_MESSAGE_LENGTH ||
                isUrl(content) ||
                isPureEmoji(content)
    }

    /**
     * 判断是否为URL
     */
    private fun isUrl(content: String): Boolean {
        return content.startsWith("http://") || content.startsWith("https://")
    }

    /**
     * 判断是否为纯表情包
     *
     * 检测Unicode表情符号范围
     */
    private fun isPureEmoji(content: String): Boolean {
        return content.all { char ->
            char.code in 0x1F300..0x1F9FF ||
                    char.code in 0x2600..0x26FF ||
                    char.code in 0x2700..0x27BF
        }
    }

    /**
     * 添加或更新词汇
     *
     * 如果词汇已存在，则更新其信息并增加热度；
     * 如果是新词汇，则以默认热度添加
     *
     * @param botMark 机器人标识
     * @param groupId 群组ID
     * @param slangWord 流行语数据
     */
    @OptIn(ExperimentalTime::class)
    fun addOrUpdateWord(
        botMark: String,
        groupId: String,
        slangWord: SlangWord
    ) = transaction {
        val existing = LearnedVocabEntity.find {
            (LearnedVocabTable.botMark eq botMark) and
                    (LearnedVocabTable.groupId eq groupId) and
                    (LearnedVocabTable.word eq slangWord.word)
        }.firstOrNull()

        if (existing != null) {
            existing.apply {
                type = slangWord.type
                meaning = slangWord.meaning
                example = slangWord.example
                weight = (weight + WEIGHT_INCREASE_ON_USE).coerceAtMost(100)
                lastSeen = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            }
            log.debug("更新词汇成功, word=${slangWord.word}, newWeight=${existing.weight}, groupId=$groupId")
        } else {
            LearnedVocabEntity.new {
                this.botMark = botMark
                this.groupId = groupId
                word = slangWord.word
                type = slangWord.type
                meaning = slangWord.meaning
                example = slangWord.example
                weight = DEFAULT_WEIGHT
                lastSeen = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            }
            log.debug("新增词汇成功, word=${slangWord.word}, weight=$DEFAULT_WEIGHT, groupId=$groupId")
        }
    }

    /**
     * 获取活跃词汇（热度 >= 50）
     *
     * 用于生成 Prompt 时注入的语言风格指南
     *
     * @param botMark 机器人标识
     * @param groupId 群组ID
     * @return 活跃词汇列表，按热度降序排列
     */
    fun getActiveVocabulary(
        botMark: String,
        groupId: String,
        limit: Int = 10
    ): List<LearnedVocabEntity> = transaction {
        val vocabs = LearnedVocabEntity.find {
            (LearnedVocabTable.botMark eq botMark) and
                    (LearnedVocabTable.groupId eq groupId) and
                    (LearnedVocabTable.weight greaterEq ACTIVE_WEIGHT_THRESHOLD)
        }
            .limit(limit)
            .sortedByDescending { it.weight }
            .toList()

        log.debug("获取活跃词汇, groupId=$groupId, 数量=${vocabs.size}")
        vocabs
    }

    /**
     * 获取所有词汇（包括低热度）
     *
     * @param botMark 机器人标识
     * @param groupId 群组ID
     * @return 所有词汇列表
     */
    fun getAllVocabulary(
        botMark: String,
        groupId: String
    ): List<LearnedVocabEntity> = transaction {
        val vocabs = LearnedVocabEntity.find {
            (LearnedVocabTable.botMark eq botMark) and
                    (LearnedVocabTable.groupId eq groupId)
        }.toList()

        log.debug("获取所有词汇, groupId=$groupId, 数量=${vocabs.size}")
        vocabs
    }

    /**
     * 热度衰减机制 - 遗忘过气的梗
     *
     * 规则：
     * 1. 如果词汇在最近3天的聊天记录中出现，热度+10
     * 2. 如果词汇在最近3天没有出现，热度-10
     * 3. 当热度 < 20 时，从词汇库中删除（遗忘机制）
     *
     * @param botMark 机器人标识
     * @param groupId 群组ID
     * @param recentMessages 最近的消息列表，用于检测词汇是否仍在使用
     */
    @OptIn(ExperimentalTime::class)
    fun decayOldWords(
        botMark: String,
        groupId: String,
        recentMessages: List<String>
    ) = transaction {
        val threeDaysAgo = Clock.System.now().minus(3.days).toLocalDateTime(TimeZone.currentSystemDefault())

        log.debug("开始执行词汇热度衰减, groupId=$groupId")

        var decayedCount = 0
        var deletedCount = 0
        var reinforcedCount = 0

        LearnedVocabEntity.find {
            (LearnedVocabTable.botMark eq botMark) and
                    (LearnedVocabTable.groupId eq groupId)
        }.forEach { vocab ->
            val wordUsedRecently = recentMessages.any { it.contains(vocab.word, ignoreCase = true) }

            if (wordUsedRecently) {
                vocab.weight = (vocab.weight + WEIGHT_INCREASE_ON_USE).coerceAtMost(100)
                vocab.lastSeen = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                reinforcedCount++
                log.debug("词汇仍在使用，热度增加, word=${vocab.word}, newWeight=${vocab.weight}")
            } else if (vocab.lastSeen < threeDaysAgo) {
                val oldWeight = vocab.weight
                vocab.weight = (vocab.weight - WEIGHT_DECAY_PER_DAY).coerceAtLeast(0)
                decayedCount++
                log.debug("词汇未使用，热度衰减, word=${vocab.word}, oldWeight=$oldWeight, newWeight=${vocab.weight}")
            }

            if (vocab.weight < MIN_WEIGHT_THRESHOLD) {
                log.debug("词汇热度过低，执行遗忘, word=${vocab.word}, weight=${vocab.weight}")
                vocab.delete()
                deletedCount++
            }
        }

        log.debug("词汇热度衰减完成, groupId=$groupId, 衰减=$decayedCount, 强化=$reinforcedCount, 遗忘=$deletedCount")
    }

    /**
     * 增加词汇热度（用于强化学习）
     *
     * 场景：如果机器人用了梗，且用户回复"哈哈哈"或点赞，该梗热度 +10
     *
     * @param botMark 机器人标识
     * @param groupId 群组ID
     * @param word 词汇
     */
    fun increaseWeight(
        botMark: String,
        groupId: String,
        word: String
    ) = transaction {
        LearnedVocabEntity.find {
            (LearnedVocabTable.botMark eq botMark) and
                    (LearnedVocabTable.groupId eq groupId) and
                    (LearnedVocabTable.word eq word)
        }.firstOrNull()?.apply {
            weight = (weight + WEIGHT_INCREASE_ON_USE).coerceAtMost(100)
            log.debug("增加词汇热度（正向反馈）, word=$word, newWeight=$weight, groupId=$groupId")
        }
    }

    /**
     * 降低词汇热度（用于负面反馈）
     *
     * 场景：如果机器人用了梗，用户回复"你有病吧"或"看不懂"，该梗热度 -50（立即止损）
     *
     * @param botMark 机器人标识
     * @param groupId 群组ID
     * @param word 词汇
     */
    fun decreaseWeight(
        botMark: String,
        groupId: String,
        word: String
    ) = transaction {
        LearnedVocabEntity.find {
            (LearnedVocabTable.botMark eq botMark) and
                    (LearnedVocabTable.groupId eq groupId) and
                    (LearnedVocabTable.word eq word)
        }.firstOrNull()?.apply {
            weight = (weight - WEIGHT_DECREASE_ON_NEGATIVE).coerceAtLeast(0)
            log.debug("降低词汇热度（负面反馈）, word=$word, newWeight=$weight, groupId=$groupId")

            if (weight < MIN_WEIGHT_THRESHOLD) {
                log.debug("词汇因负面反馈被遗忘, word=$word, groupId=$groupId")
                delete()
            }
        }
    }
}

/**
 * 捕获的消息数据结构
 *
 * @property timestamp 消息时间戳
 * @property userId 用户ID
 * @property content 消息内容
 */
data class CapturedMessage(
    val timestamp: LocalDateTime,
    val userId: String,
    val content: String
)