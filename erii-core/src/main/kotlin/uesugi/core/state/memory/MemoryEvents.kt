package uesugi.core.state.memory

/**
 * 记忆模块事件定义
 *
 * 定义了记忆系统中的事件：
 * - [MemoryExtractedEvent]: 记忆提取完成时触发
 * - [UserProfileUpdatedEvent]: 用户画像更新时触发
 * - [SummaryGeneratedEvent]: 对话摘要生成时触发
 */

/**
 * 记忆提取事件
 *
 * 当从群聊消息中提取出新的事实记忆时触发
 *
 * @property botId 机器人标识
 * @property groupId 群组ID
 * @property factsCount 提取的事实数量
 */
data class MemoryExtractedEvent(
    val botId: String,
    val groupId: String,
    val factsCount: Int
)

/**
 * 用户画像更新事件
 *
 * 当用户画像发生变化时触发
 *
 * @property userId 用户ID
 * @property botId 机器人标识
 * @property groupId 群组ID
 * @property profileChanges 画像变更内容
 */
data class UserProfileUpdatedEvent(
    val userId: String,
    val botId: String,
    val groupId: String,
    val profileChanges: Map<String, Any>
)

/**
 * 对话摘要生成事件
 *
 * 当对话摘要生成完成时触发
 *
 * @property botId 机器人标识
 * @property groupId 群组ID
 * @property summaryId 摘要记录ID
 * @property messageCount 涉及的消息数量
 */
data class SummaryGeneratedEvent(
    val botId: String,
    val groupId: String,
    val summaryId: Int,
    val messageCount: Int
)
