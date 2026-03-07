package uesugi.core.state.meme

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.andWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.core.message.history.HistoryTable
import uesugi.core.message.history.MessageType
import uesugi.core.message.resource.ResourceTable
import uesugi.toolkit.logger
import kotlin.time.Clock.System
import kotlin.time.ExperimentalTime

class MemoService(
    private val vectorStoreFactory: MemoVectorStoreFactory
) {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        const val ANALYZE_THRESHOLD = 3 // 累计出现3次开始分析
        const val MAX_CONTEXTS = 400 // 最大上下文条数
        private val log = logger()
    }

    /**
     * 获取群组中最近的图片消息及其MD5
     * @param lastHistoryId 只获取 id 大于此值的消息（用于增量扫描）
     */
    fun getRecentImageMessages(
        botId: String,
        groupId: String,
        lastHistoryId: Int = 0,
        limit: Int = 500
    ): List<ImageMessageWithMd5> {
        return transaction {
            val query = HistoryTable
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
                .where {
                    (HistoryTable.botMark eq botId) and
                            (HistoryTable.groupId eq groupId) and
                            (HistoryTable.messageType eq MessageType.IMAGE)
                }

            // 如果有 lastHistoryId，则只获取更大的 id
            if (lastHistoryId > 0) {
                query.andWhere { HistoryTable.id greater lastHistoryId }
            }

            query
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

    /**
     * 获取待分析的表情包
     * 条件：seenCount >= 3 且 seenCount 是 3 的倍数 且 seenCount > lastAnalyzedCount
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
     * @param botId 机器人标识
     * @param groupId 群组ID，如果为空则返回所有群组的表情包
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
     * 根据MD5查找已存在的表情包
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
     * - 如果不存在：创建新记录
     * - 如果存在：追加上下文，增加计数（上下文最大400条）
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

                if (context != null && context.isNotBlank() && context !in existingContexts) {
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
                val newContexts = if (context != null && context.isNotBlank()) {
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
     */
    fun getMemoById(id: Int): MemeRecord? {
        return transaction {
            MemeEntity.findById(id)?.toRecord()
        }
    }

    /**
     * 获取所有表情包
     */
    fun getAllMemos(botId: String, groupId: String): List<MemeRecord> {
        return transaction {
            MemeEntity.find {
                (MemeTable.botMark eq botId) and (MemeTable.groupId eq groupId)
            }.map { it.toRecord() }
        }
    }

    /**
     * 将表情包添加到向量存储
     */
    suspend fun upsertToVectorStore(memo: MemeRecord) {
        val store = vectorStoreFactory.getStore(memo.botId, memo.groupId)

        // 构建用于向量化的文本内容
        val content = buildString {
            memo.purpose?.let {
                if (isNotEmpty()) append(" ")
                append(it)
            }
            memo.tags?.let {
                if (isNotEmpty()) append(" ")
                append(it)
            }
        }

        if (content.isNotBlank()) {
            val vector = TextImageEncoder.encode(content, memo.resource?.bytes)
            val vectorId = memo.vectorId ?: TextImageEncoder.generateVectorId(memo.botId, memo.groupId, memo.id!!)
            store.upsert(vectorId, content, "", vector)
            log.debug("表情包向量已存储: vectorId=$vectorId, content=$content")
        }
    }

    /**
     * 从向量存储中删除表情包
     */
    fun deleteFromVectorStore(botId: String, groupId: String, vectorId: String) {
        val store = vectorStoreFactory.getStore(botId, groupId)
        store.delete(vectorId)
        log.debug("表情包向量已删除: vectorId=$vectorId")
    }

    /**
     * 使用向量搜索表情包
     */
    suspend fun searchByVector(
        botId: String,
        groupId: String?,
        query: String,
        topK: Int
    ): List<Pair<MemeRecord, Float>> {
        // 生成查询向量
        val queryVector = TextImageEncoder.encode(query)

        // 确定搜索范围
        val targetGroupId = groupId?.takeIf { it.isNotBlank() }

        return if (targetGroupId != null) {
            // 在指定群组中搜索
            val store = vectorStoreFactory.getStore(botId, targetGroupId)
            val results = store.search(queryVector, topK, null)
            results.mapNotNull { result ->
                val memoId = TextImageEncoder.extractMemoId(result.id)
                memoId?.let { getMemoById(it) }?.let { it to result.score }
            }
        } else {
            // 在所有群组中搜索 - 需要遍历所有群组的向量存储
            val results = mutableListOf<Pair<MemeRecord, Float>>()

            // 获取该bot下所有已分析的群组
            val allMemos = getAnalyzedMemes(botId, "")
            val groupIds = allMemos.map { it.groupId }.distinct()

            for (gid in groupIds) {
                if (gid.isBlank()) continue
                try {
                    val store = vectorStoreFactory.getStore(botId, gid)
                    val searchResults = store.search(queryVector, topK, null)
                    searchResults.forEach { result ->
                        val memoId = TextImageEncoder.extractMemoId(result.id)
                        memoId?.let { id ->
                            getMemoById(id)?.let { results.add(it to result.score) }
                        }
                    }
                } catch (e: Exception) {
                    log.warn("搜索群组 $gid 向量存储失败: ${e.message}")
                }
            }

            // 按相似度排序（这里简化为按使用次数）
            results.distinctBy { it.first.id }.sortedByDescending { it.second }.take(topK)
        }
    }

    /**
     * 获取扫描状态
     */
    fun getScanState(botId: String, groupId: String): MemeScanStateRecord? {
        return transaction {
            MemeScanStateEntity.find {
                (MemoScanStateTable.botMark eq botId) and (MemoScanStateTable.groupId eq groupId)
            }.firstOrNull()?.toRecord()
        }
    }

    /**
     * 更新扫描状态
     */
    @OptIn(ExperimentalTime::class)
    fun updateScanState(botId: String, groupId: String, lastHistoryId: Int) {
        transaction {
            val existing = MemeScanStateEntity.find {
                (MemoScanStateTable.botMark eq botId) and (MemoScanStateTable.groupId eq groupId)
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
}

data class ImageMessageWithMd5(
    val historyId: Int,
    val content: String?,
    val resourceId: Int,
    val md5: String,
    val createdAt: LocalDateTime
)
