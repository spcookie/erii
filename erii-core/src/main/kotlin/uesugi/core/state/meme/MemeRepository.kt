package uesugi.core.state.meme

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.HistoryTable
import uesugi.common.MessageType
import uesugi.common.logger
import uesugi.core.message.resource.ResourceTable
import uesugi.core.state.meme.MemeData.MemeEntity
import uesugi.core.state.meme.MemeData.MemeRecord
import uesugi.core.state.meme.MemeData.MemeScanStateEntity
import uesugi.core.state.meme.MemeData.MemeScanStateRecord
import uesugi.core.state.meme.MemeData.MemeScanStateTable
import uesugi.core.state.meme.MemeData.MemeTable
import uesugi.core.state.meme.MemeData.toRecord
import kotlin.time.Clock.System
import kotlin.time.ExperimentalTime

/**
 * 表情包仓库 - 负责数据库操作
 *
 * 提供表情包的 CRUD 操作，包括：
 * - 查找、创建、更新表情包记录
 * - 获取待分析的表情包
 * - 扫描状态管理
 * - 使用统计
 */
class MemeRepository {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val ANALYZE_THRESHOLD = 3 // 累计出现3次开始分析
        const val MAX_CONTEXTS = 400 // 最大上下文条数
        private val log = logger()
    }

    /**
     * 根据MD5查找已存在的表情包
     *
     * @param botId 机器人标识
     * @param groupId 群组ID
     * @param md5 图片MD5值
     * @return 表情包记录，如果不存在则返回 null
     */
    fun findByMd5(botId: String, groupId: String, md5: String): MemeRecord? {
        return transaction {
            MemeEntity.find {
                (MemeTable.botMark eq botId) and
                        (MemeTable.groupId eq groupId) and
                        (MemeTable.md5 eq md5)
            }.firstOrNull()?.toRecord()
        }
    }

    /**
     * 添加或更新表情包
     *
     * - 如果不存在：创建新记录
     * - 如果存在：追加上下文，增加计数（上下文最大400条）
     *
     * @param botId 机器人标识
     * @param groupId 群组ID
     * @param resourceId 资源ID
     * @param md5 图片MD5值
     * @param context 上下文消息
     * @return 更新后的表情包记录
     */
    @OptIn(ExperimentalTime::class)
    fun addOrUpdateMeme(
        botId: String,
        groupId: String,
        resourceId: Int,
        md5: String,
        context: String?
    ): MemeRecord {
        return transaction {
            val existing = MemeEntity.find {
                (MemeTable.botMark eq botId) and
                        (MemeTable.groupId eq groupId) and
                        (MemeTable.md5 eq md5)
            }.firstOrNull()

            if (existing != null) {
                // 已存在：追加上下文，增加计数
                val existingContexts = try {
                    json.decodeFromString<List<String>>(existing.contexts).toMutableList()
                } catch (_: Exception) {
                    mutableListOf()
                }

                if (!context.isNullOrBlank() && context !in existingContexts) {
                    existingContexts.add(context)
                    // 限制最大400条，保留最新的
                    if (existingContexts.size > MAX_CONTEXTS) {
                        existingContexts.removeAt(0)
                    }
                }

                existing.contexts = json.encodeToString(existingContexts)
                existing.seenCount = existing.seenCount + 1
                existing.updatedAt = System.now().toLocalDateTime(TimeZone.currentSystemDefault())

                log.debug("更新表情包: md5=$md5, seenCount=${existing.seenCount}, contexts=${existingContexts.size}")
                existing.toRecord()
            } else {
                // 新建记录
                val newContexts = if (!context.isNullOrBlank()) {
                    listOf(context)
                } else {
                    emptyList()
                }

                MemeEntity.new {
                    this.botMark = botId
                    this.groupId = groupId
                    this.resourceId = resourceId
                    this.md5 = md5
                    this.contexts = json.encodeToString(newContexts)
                    this.seenCount = 1
                    this.lastAnalyzedCount = 0
                    this.usageCount = 0
                    this.createdAt = System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    this.updatedAt = System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                }.toRecord()
            }
        }
    }

    /**
     * 更新分析结果
     *
     * @param memeId 表情包ID
     * @param description 描述
     * @param purpose 用途
     * @param tags 标签
     * @param vectorId 向量ID
     * @param analyzedCount 分析时的累计计数
     */
    @OptIn(ExperimentalTime::class)
    fun updateAnalysis(
        memeId: Int,
        description: String,
        purpose: String,
        tags: String,
        vectorId: String,
        analyzedCount: Int
    ) {
        transaction {
            val memo = MemeEntity.findById(memeId)
            memo?.let {
                it.lastAnalyzedCount = analyzedCount
                it.description = description
                it.purpose = purpose
                it.tags = tags
                it.vectorId = vectorId
                it.updatedAt = System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                log.debug("更新分析结果: memeId=$memeId, analyzedCount=$analyzedCount, description=$description")
            }
        }
    }

    /**
     * 更新使用次数
     *
     * @param memeId 表情包ID
     */
    @OptIn(ExperimentalTime::class)
    fun incrementUsageCount(memeId: Int) {
        transaction {
            val memo = MemeEntity.findById(memeId)
            memo?.let {
                it.usageCount += 1
                it.lastUsedAt = System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            }
        }
    }

    /**
     * 根据ID获取表情包
     *
     * @param id 表情包ID
     * @return 表情包记录
     */
    fun getMemoById(id: Int): MemeRecord? {
        return transaction {
            MemeEntity.findById(id)?.toRecord()
        }
    }

    /**
     * 获取所有表情包
     *
     * @param botId 机器人标识
     * @param groupId 群组ID
     * @return 表情包记录列表
     */
    fun getAllMemos(botId: String, groupId: String): List<MemeRecord> {
        return transaction {
            MemeEntity.find {
                (MemeTable.botMark eq botId) and (MemeTable.groupId eq groupId)
            }.map { it.toRecord() }
        }
    }

    /**
     * 获取待分析的表情包
     *
     * 条件：seenCount >= 3 且 seenCount 是 3 的倍数 且 seenCount > lastAnalyzedCount
     *
     * @param botId 机器人标识
     * @param groupId 群组ID
     * @return 待分析的表情包列表
     */
    fun getPendingAnalysisMemes(botId: String, groupId: String): List<MemeRecord> {
        return transaction {
            MemeEntity.find {
                (MemeTable.botMark eq botId) and
                        (MemeTable.groupId eq groupId) and
                        (MemeTable.seenCount greaterEq ANALYZE_THRESHOLD)
            }.map { it.toRecord() }
                .filter { memo ->
                    // seenCount 是 3 的倍数 且 大于上次分析的计数
                    val isMultipleOf3 = memo.seenCount % ANALYZE_THRESHOLD == 0
                    val needsReanalysis = memo.seenCount > memo.lastAnalyzedCount
                    isMultipleOf3 && needsReanalysis
                }
        }
    }

    /**
     * 获取所有已分析的表情包（用于搜索）
     *
     * @param botId 机器人标识
     * @param groupId 群组ID，如果为空则返回所有群组的表情包
     * @return 已分析的表情包列表
     */
    fun getAnalyzedMemes(botId: String, groupId: String): List<MemeRecord> {
        return transaction {
            val query = if (groupId.isBlank()) {
                // 获取所有群组的已分析表情包
                MemeEntity.find {
                    (MemeTable.botMark eq botId) and
                            (MemeTable.lastAnalyzedCount greaterEq ANALYZE_THRESHOLD)
                }
            } else {
                // 获取指定群组的已分析表情包
                MemeEntity.find {
                    (MemeTable.botMark eq botId) and
                            (MemeTable.groupId eq groupId) and
                            (MemeTable.lastAnalyzedCount greaterEq ANALYZE_THRESHOLD)
                }
            }
            query.map { it.toRecord() }
        }
    }

    /**
     * 获取扫描状态
     *
     * @param botId 机器人标识
     * @param groupId 群组ID
     * @return 扫描状态记录
     */
    fun getScanState(botId: String, groupId: String): MemeScanStateRecord? {
        return transaction {
            MemeScanStateEntity.find {
                (MemeScanStateTable.botMark eq botId) and (MemeScanStateTable.groupId eq groupId)
            }.firstOrNull()?.toRecord()
        }
    }

    /**
     * 更新扫描状态
     *
     * @param botId 机器人标识
     * @param groupId 群组ID
     * @param lastHistoryId 最后扫描的 history id
     */
    @OptIn(ExperimentalTime::class)
    fun updateScanState(botId: String, groupId: String, lastHistoryId: Int) {
        transaction {
            val existing = MemeScanStateEntity.find {
                (MemeScanStateTable.botMark eq botId) and (MemeScanStateTable.groupId eq groupId)
            }.firstOrNull()

            if (existing != null) {
                existing.lastHistoryId = lastHistoryId
                existing.lastScanAt = System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            } else {
                MemeScanStateEntity.new {
                    this.botMark = botId
                    this.groupId = groupId
                    this.lastHistoryId = lastHistoryId
                    this.lastScanAt = System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                }
            }
            log.debug("更新扫描状态: botId=$botId, groupId=$groupId, lastHistoryId=$lastHistoryId")
        }
    }

    /**
     * 获取群组中最近的图片消息及其MD5
     *
     * @param botId 机器人标识
     * @param groupId 群组ID
     * @param lastHistoryId 只获取 id 大于此值的消息（用于增量扫描）
     * @param limit 最多返回条数
     * @return 图片消息列表
     */
    fun getRecentImageMessages(
        botId: String,
        groupId: String,
        lastHistoryId: Int = 0,
        limit: Int = 500
    ): List<ImageMessageWithMd5> {
        return transaction {
            // 构建基础条件
            val baseCondition = (HistoryTable.botMark eq botId) and
                    (HistoryTable.groupId eq groupId) and
                    (HistoryTable.messageType eq MessageType.IMAGE)

            // 如果有 lastHistoryId，则添加条件
            val condition = if (lastHistoryId > 0) {
                baseCondition and (HistoryTable.id greater lastHistoryId)
            } else {
                baseCondition
            }

            HistoryTable
                .join(
                    ResourceTable,
                    JoinType.LEFT,
                    additionalConstraint = { ResourceTable.id eq HistoryTable.resourceId }
                )
                .select(
                    HistoryTable.id,
                    HistoryTable.content,
                    HistoryTable.createdAt,
                    ResourceTable.id,
                    ResourceTable.md5
                )
                .where { condition }
                .orderBy(HistoryTable.id, SortOrder.ASC)
                .limit(limit)
                .map { row ->
                    ImageMessageWithMd5(
                        historyId = row[HistoryTable.id].value,
                        content = row[HistoryTable.content],
                        resourceId = row[ResourceTable.id].value,
                        md5 = row[ResourceTable.md5],
                        createdAt = row[HistoryTable.createdAt]
                    )
                }
        }
    }
}
