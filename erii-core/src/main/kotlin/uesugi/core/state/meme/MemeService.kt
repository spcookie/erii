package uesugi.core.state.meme

import kotlinx.datetime.LocalDateTime
import uesugi.common.logger
import uesugi.core.state.meme.MemeData.MemeRecord
import uesugi.core.state.meme.MemeData.MemeScanStateRecord

/**
 * 表情包服务 - 业务逻辑层
 *
 * 负责表情包的业务操作，包括：
 * - 添加或更新表情包
 * - 获取待分析的表情包
 * - 向量存储操作
 * - 表情包搜索
 *
 * 数据库操作通过 [MemeRepository] 完成
 *
 * @property vectorStoreFactory 向量存储工厂
 * @property repository 表情包仓库
 */
class MemoService(
    private val vectorStoreFactory: MemoVectorStore,
    private val repository: MemeRepository
) {

    companion object {
        private val log = logger()
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
        return repository.getPendingAnalysisMemes(botId, groupId)
    }

    /**
     * 获取所有已分析的表情包（用于搜索）
     *
     * @param botId 机器人标识
     * @param groupId 群组ID，如果为空则返回所有群组的表情包
     * @return 已分析的表情包列表
     */
    fun getAnalyzedMemes(botId: String, groupId: String): List<MemeRecord> {
        return repository.getAnalyzedMemes(botId, groupId)
    }

    /**
     * 根据MD5查找已存在的表情包
     *
     * @param botId 机器人标识
     * @param groupId 群组ID
     * @param md5 图片MD5值
     * @return 表情包记录
     */
    fun findByMd5(botId: String, groupId: String, md5: String): MemeRecord? {
        return repository.findByMd5(botId, groupId, md5)
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
    fun addOrUpdateMeme(
        botId: String,
        groupId: String,
        resourceId: Int,
        md5: String,
        context: String?
    ): MemeRecord {
        return repository.addOrUpdateMeme(botId, groupId, resourceId, md5, context)
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
    fun updateAnalysis(
        memeId: Int,
        description: String,
        purpose: String,
        tags: String,
        vectorId: String,
        analyzedCount: Int
    ) {
        repository.updateAnalysis(memeId, description, purpose, tags, vectorId, analyzedCount)
    }

    /**
     * 更新使用次数
     *
     * @param memeId 表情包ID
     */
    fun incrementUsageCount(memeId: Int) {
        repository.incrementUsageCount(memeId)
    }

    /**
     * 根据ID获取表情包
     *
     * @param id 表情包ID
     * @return 表情包记录
     */
    fun getMemoById(id: Int): MemeRecord? {
        return repository.getMemoById(id)
    }

    /**
     * 获取所有表情包
     *
     * @param botId 机器人标识
     * @param groupId 群组ID
     * @return 表情包记录列表
     */
    fun getAllMemos(botId: String, groupId: String): List<MemeRecord> {
        return repository.getAllMemos(botId, groupId)
    }

    /**
     * 将表情包添加到向量存储
     *
     * @param memo 表情包记录
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
            val vector = vectorStoreFactory.encode(content, memo.resource?.bytes)
            val vectorId = memo.vectorId ?: vectorStoreFactory.generateVectorId(memo.botId, memo.groupId, memo.id!!)
            store.upsert(vectorId, content, "", vector)
            log.debug("表情包向量已存储: vectorId=$vectorId, content=$content")
        }
    }

    /**
     * 从向量存储中删除表情包
     *
     * @param botId 机器人标识
     * @param groupId 群组ID
     * @param vectorId 向量ID
     */
    fun deleteFromVectorStore(botId: String, groupId: String, vectorId: String) {
        val store = vectorStoreFactory.getStore(botId, groupId)
        store.delete(vectorId)
        log.debug("表情包向量已删除: vectorId=$vectorId")
    }

    /**
     * 使用向量搜索表情包
     *
     * @param botId 机器人标识
     * @param groupId 群组ID（可选）
     * @param query 搜索关键词
     * @param topK 返回结果数量
     * @return 表情包列表及相似度分数
     */
    suspend fun searchByVector(
        botId: String,
        groupId: String?,
        query: String,
        topK: Int
    ): List<Pair<MemeRecord, Float>> {
        // 生成查询向量
        val queryVector = vectorStoreFactory.encode(query)

        // 确定搜索范围
        val targetGroupId = groupId?.takeIf { it.isNotBlank() }

        return if (targetGroupId != null) {
            // 在指定群组中搜索
            val store = vectorStoreFactory.getStore(botId, targetGroupId)
            val results = store.search(queryVector, topK, null)
            results.mapNotNull { result ->
                val memoId = vectorStoreFactory.extractMemoId(result.id)
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
                        val memoId = vectorStoreFactory.extractMemoId(result.id)
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
     *
     * @param botId 机器人标识
     * @param groupId 群组ID
     * @return 扫描状态记录
     */
    fun getScanState(botId: String, groupId: String): MemeScanStateRecord? {
        return repository.getScanState(botId, groupId)
    }

    /**
     * 更新扫描状态
     *
     * @param botId 机器人标识
     * @param groupId 群组ID
     * @param lastHistoryId 最后扫描的 history id
     */
    fun updateScanState(botId: String, groupId: String, lastHistoryId: Int) {
        repository.updateScanState(botId, groupId, lastHistoryId)
    }

    /**
     * 获取群组中最近的图片消息及其MD5
     *
     * 注意：此方法已移至 [MemeRepository]
     *
     * @deprecated 使用 [MemeRepository.getRecentImageMessages] 代替
     */
    fun getRecentImageMessages(
        botId: String,
        groupId: String,
        lastHistoryId: Int,
        limit: Int
    ): List<ImageMessageWithMd5> {
        return repository.getRecentImageMessages(botId, groupId, lastHistoryId, limit)
    }
}

/**
 * 图片消息及MD5值
 *
 * @property historyId 历史消息ID
 * @property content 消息内容
 * @property resourceId 资源ID
 * @property md5 图片MD5值
 * @property createdAt 创建时间
 */
data class ImageMessageWithMd5(
    val historyId: Int,
    val content: String?,
    val resourceId: Int,
    val md5: String,
    val createdAt: LocalDateTime
)
