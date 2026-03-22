package uesugi.core.state.meme

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import okio.Path.Companion.toPath
import okio.buffer
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.scheduling.BackgroundJob
import uesugi.BotManage
import uesugi.common.ENABLE_GROUPS
import uesugi.common.HistoryTable
import uesugi.common.MessageType
import uesugi.common.logger
import uesugi.core.component.ObjectStorage
import uesugi.core.message.resource.ResourceService

/**
 * 表情包调度任务
 *
 * 整合了表情包收集和提取两个任务：
 * 1. 收集任务 [doCollecting]: 扫描历史消息中的图片，收集表情包
 * 2. 提取任务 [doExtracting]: 分析待处理的表情包，提取描述、用途、标签
 *
 * 调度周期：
 * - 收集任务：每 30 分钟执行一次
 * - 提取任务：每 1 小时执行一次
 *
 * @property memeService 表情包服务
 * @property resourceService 资源服务
 * @property storage 对象存储
 * @property memeAgent 表情包分析 Agent
 */
class MemeJob(
    private val memeService: MemoService,
    private val resourceService: ResourceService,
    private val storage: ObjectStorage,
    private val memeAgent: MemeAgent,
    private val vectorStoreFactory: MemoVectorStore
) {
    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()

    /**
     * 开启定时触发
     *
     * 启动两个定时任务：
     * - 收集任务：每 30 分钟执行一次
     * - 提取任务：每 1 小时执行一次
     */
    fun openTimingTriggerSignal() {
        // 收集任务：每 30 分钟一次
        BackgroundJob.scheduleRecurrently(
            "meme-collect-job",
            "0,30 * * * *",
            ::doCollecting
        )

        // 提取任务：每 1 小时一次
        BackgroundJob.scheduleRecurrently(
            "meme-extract-job",
            "0 */1 * * *",
            ::doExtracting
        )

        log.info("表情包任务已启动 - 收集: 30分钟, 提取: 1小时")
    }

    /**
     * 执行表情包收集任务
     *
     * 扫描历史消息中的图片：
     * 1. 如果 md5 已存在，追加上下文，计数+1
     * 2. 如果 md5 不存在，创建新记录
     * 3. 累计计数达到3后，标记待分析
     *
     * 使用扫描状态表记录最后扫描的 history id，避免重复扫描
     */
    fun doCollecting() {
        runBlocking {
            if (mutex.tryLock()) {
                try {
                    log.debug("表情包收集任务开始执行")

                    val groups = ENABLE_GROUPS
                    log.debug("需要收集的群组: ${groups.size} 个")

                    for (groupId in groups) {
                        for (botId in BotManage.getAllBotIds()) {
                            processGroupCollection(botId, groupId)
                        }
                    }

                    log.debug("表情包收集任务执行完成")
                } catch (e: Exception) {
                    log.error("表情包收集任务执行失败", e)
                } finally {
                    mutex.unlock()
                }
            } else {
                log.debug("表情包收集任务正在执行中, 跳过本次调度")
            }
        }
    }

    /**
     * 处理单个群组的表情包收集
     *
     * @param botId 机器人标识
     * @param groupId 群组ID
     */
    private fun processGroupCollection(botId: String, groupId: String) {
        log.debug("开始收集群组表情包, 群组ID: $groupId")

        try {
            // 获取扫描状态（上次扫描到的最后 history id）
            val scanState = memeService.getScanState(botId, groupId)
            val lastHistoryId = scanState?.lastHistoryId ?: 0
            log.debug("上次扫描到的最后 history id: $lastHistoryId")

            // 获取增量图片消息（只获取 id > lastHistoryId 的）
            val newImages = memeService.getRecentImageMessages(
                botId = botId,
                groupId = groupId,
                lastHistoryId = lastHistoryId,
                limit = 500
            )

            if (newImages.isEmpty()) {
                log.debug("群组 $groupId 没有新的图片消息需要处理")
                return
            }

            log.debug("发现 ${newImages.size} 张新图片需要处理")

            var maxHistoryId = lastHistoryId

            for (image in newImages) {
                try {
                    // 更新最大 history id
                    if (image.historyId > maxHistoryId) {
                        maxHistoryId = image.historyId
                    }

                    if (image.md5.isBlank()) {
                        log.warn("图片 ${image.resourceId} 没有MD5，跳过")
                        continue
                    }

                    // 获取上下文
                    val context = getContextMessage(botId, groupId, image.historyId)

                    // 添加或更新表情包
                    memeService.addOrUpdateMeme(
                        botId = botId,
                        groupId = groupId,
                        resourceId = image.resourceId,
                        md5 = image.md5,
                        context = context
                    )

                    log.debug("处理图片: md5=${image.md5}, historyId=${image.historyId}")
                } catch (e: Exception) {
                    log.error("处理图片 ${image.resourceId} 失败", e)
                }
            }

            // 更新扫描状态
            if (maxHistoryId > lastHistoryId) {
                memeService.updateScanState(botId, groupId, maxHistoryId)
                log.debug("扫描状态已更新: lastHistoryId=$maxHistoryId")
            }

            log.debug("群组 $groupId 表情包收集完成")

        } catch (e: Exception) {
            log.error("处理群组 $groupId 表情包收集失败", e)
        }
    }

    /**
     * 获取指定图片消息前后的文本上下文
     *
     * @param botMark 机器人标识
     * @param groupId 群组ID
     * @param historyId 图片消息的 history id
     * @return 上下文消息文本
     */
    private fun getContextMessage(botMark: String, groupId: String, historyId: Int): String? {
        return transaction {
            // 获取该图片之前的文本消息（id < historyId 的最近的消息）
            val beforeMessages = HistoryTable
                .select(HistoryTable.content)
                .where {
                    (HistoryTable.botMark eq botMark) and
                            (HistoryTable.groupId eq groupId) and
                            (HistoryTable.messageType eq MessageType.TEXT) and
                            (HistoryTable.id less historyId)
                }
                .orderBy(HistoryTable.id, SortOrder.DESC)
                .limit(10)
                .mapNotNull { it[HistoryTable.content] }
                .reversed() // 按时间正序

            // 获取该图片之后的文本消息（id > historyId 的最近的消息）
            val afterMessages = HistoryTable
                .select(HistoryTable.content)
                .where {
                    (HistoryTable.botMark eq botMark) and
                            (HistoryTable.groupId eq groupId) and
                            (HistoryTable.messageType eq MessageType.TEXT) and
                            (HistoryTable.id greater historyId)
                }
                .orderBy(HistoryTable.id, SortOrder.ASC)
                .limit(10)
                .mapNotNull { it[HistoryTable.content] }

            // 合并前后消息
            val allContexts = beforeMessages + afterMessages

            allContexts.joinToString("\n").takeIf { it.isNotBlank() }
        }
    }

    /**
     * 执行表情包提取任务
     *
     * 定时分析待处理的表情包：
     * 1. 获取 seenCount >= 3 的未分析表情包
     * 2. 调用 LLM 提取描述、用途、标签
     * 3. 生成向量嵌入
     */
    fun doExtracting() {
        runBlocking {
            if (mutex.tryLock()) {
                try {
                    log.debug("表情包提取任务开始执行")

                    val groups = ENABLE_GROUPS
                    log.debug("需要提取的群组: ${groups.size} 个")

                    for (groupId in groups) {
                        for (botId in BotManage.getAllBotIds()) {
                            processGroupExtraction(botId, groupId)
                        }
                    }

                    log.debug("表情包提取任务执行完成")
                } catch (e: Exception) {
                    log.error("表情包提取任务执行失败", e)
                } finally {
                    mutex.unlock()
                }
            } else {
                log.debug("表情包提取任务正在执行中, 跳过本次调度")
            }
        }
    }

    /**
     * 处理单个群组的表情包提取
     *
     * @param botMark 机器人标识
     * @param groupId 群组ID
     */
    private suspend fun processGroupExtraction(botMark: String, groupId: String) {
        log.debug("开始提取群组表情包, 群组ID: $groupId")

        try {
            // 获取待分析的表情包
            val pendingMemos = memeService.getPendingAnalysisMemes(botMark, groupId)

            if (pendingMemos.isEmpty()) {
                log.debug("群组 $groupId 没有待分析的表情包")
                return
            }

            log.debug("发现 ${pendingMemos.size} 个待分析表情包")

            for (memo in pendingMemos) {
                try {
                    // 记录当前计数（用于更新 lastAnalyzedCount）
                    val currentSeenCount = memo.seenCount

                    // 调用 LLM 分析
                    val analysis = memeAgent.analyzeMeme(memo.contexts)

                    if (analysis != null) {
                        // 生成向量ID
                        val vectorId = vectorStoreFactory.generateVectorId(botMark, groupId, memo.id!!)

                        val resource = resourceService.getResource(memo.resourceId)
                            ?.apply {
                                bytes = storage.get(this.url.toPath())
                                    .buffer().readByteArray()
                            }

                        // 更新向量存储
                        val updatedMemo = memo.copy(
                            description = analysis.description,
                            purpose = analysis.purpose,
                            tags = analysis.tags.joinToString(","),
                            vectorId = vectorId,
                            lastAnalyzedCount = currentSeenCount,
                            resource = resource
                        )

                        memeService.upsertToVectorStore(updatedMemo)

                        // 更新分析结果（传入当前计数）
                        memeService.updateAnalysis(
                            memeId = memo.id,
                            description = analysis.description,
                            purpose = analysis.purpose,
                            tags = analysis.tags.joinToString(","),
                            vectorId = vectorId,
                            analyzedCount = currentSeenCount
                        )

                        log.debug("表情包分析完成: memoId=${memo.id}, seenCount=$currentSeenCount, description=${analysis.description}")
                    }
                } catch (e: Exception) {
                    log.error("分析表情包 ${memo.id} 失败", e)
                }
            }

            log.debug("群组 $groupId 表情包提取完成")

        } catch (e: Exception) {
            log.error("处理群组 $groupId 表情包提取失败", e)
        }
    }
}
