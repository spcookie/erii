package uesugi.core.state.evolution

/**
 * 进化模块事件定义
 *
 * 定义了词汇学习和提取相关的事件：
 * - [VocabularyLearnedEvent]: 新词汇学习完成时触发
 * - [VocabularyExtractedEvent]: 词汇提取完成时触发
 */

/**
 * 词汇学习事件
 *
 * 当机器人学习到新词汇时触发
 *
 * @property botId 机器人标识
 * @property groupId 群组ID
 * @property vocabulary 新学习的词汇
 * @property meaning 词汇含义
 */
data class VocabularyLearnedEvent(
    val botId: String,
    val groupId: String,
    val vocabulary: String,
    val meaning: String
)

/**
 * 词汇提取事件
 *
 * 当从消息中提取词汇完成时触发
 *
 * @property botId 机器人标识
 * @property groupId 群组ID
 * @property extractedCount 提取的词汇数量
 */
data class VocabularyExtractedEvent(
    val botId: String,
    val groupId: String,
    val extractedCount: Int
)
