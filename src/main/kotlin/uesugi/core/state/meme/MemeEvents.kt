package uesugi.core.state.meme

/**
 * 表情包模块事件定义
 *
 * 定义了表情包收集和分析过程中可能触发的事件：
 * - [MemeCollectedEvent]: 表情包被收集时触发
 * - [MemeAnalyzedEvent]: 表情包分析完成时触发
 * - [MemeSearchedEvent]: 表情包搜索时触发
 */

/**
 * 表情包收集事件
 *
 * 当新的表情包被收集或现有表情包上下文更新时触发
 *
 * @property memeId 表情包ID
 * @property botId 机器人标识
 * @property groupId 群组ID
 * @property md5 图片MD5值
 * @property seenCount 当前累计出现次数
 * @property isNew 是否是新表情包
 */
data class MemeCollectedEvent(
    val memeId: Int,
    val botId: String,
    val groupId: String,
    val md5: String,
    val seenCount: Int,
    val isNew: Boolean
)

/**
 * 表情包分析完成事件
 *
 * 当表情包的 LLM 分析完成时触发
 *
 * @property memeId 表情包ID
 * @property botId 机器人标识
 * @property groupId 群组ID
 * @param description 表情包描述
 * @param purpose 表情包用途
 * @param tags 表情包标签
 * @param vectorId 向量存储ID
 */
data class MemeAnalyzedEvent(
    val memeId: Int,
    val botId: String,
    val groupId: String,
    val description: String,
    val purpose: String,
    val tags: String,
    val vectorId: String
)

/**
 * 表情包搜索事件
 *
 * 当用户搜索表情包时触发
 *
 * @property botId 机器人标识
 * @property groupId 群组ID（可选）
 * @property query 搜索关键词
 * @property resultsCount 返回结果数量
 */
data class MemeSearchedEvent(
    val botId: String,
    val groupId: String?,
    val query: String,
    val resultsCount: Int
)
