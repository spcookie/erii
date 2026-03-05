package uesugi.core.state.meme

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import okio.Path.Companion.toPath
import okio.buffer
import org.jobrunr.scheduling.BackgroundJob
import uesugi.BotManage
import uesugi.ENABLE_GROUPS
import uesugi.core.message.resource.ResourceService
import uesugi.toolkit.ObjectStorage
import uesugi.toolkit.logger

/**
 * 表情包提取 Job
 *
 * 定时分析待处理的表情包：
 * 1. 获取 seenCount >= 3 的未分析表情包
 * 2. 调用 LLM 提取描述、用途、标签
 * 3. 生成向量嵌入
 */
class MemeExtractJob(
    private val memoService: MemoService,
    private val resourceService: ResourceService,
    private val storage: ObjectStorage,
    private val memeAgent: MemeAgent
) {
    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()

    /**
     * 开启定时触发
     *
     * 每 2 小时执行一次表情包分析
     */
    fun openTimingTriggerSignal() {
        BackgroundJob.scheduleRecurrently(
            "meme-extract-job",
            "0 */1 * * *",  // 每 1 小时一次
            ::doExtracting
        )
        log.info("表情包提取任务定时器已启动, 执行周期: 每1小时")
    }

    /**
     * 执行表情包提取
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
     */
    private suspend fun processGroupExtraction(botMark: String, groupId: String) {
        log.debug("开始提取群组表情包, 群组ID: $groupId")

        try {
            // 获取待分析的表情包
            val pendingMemos = memoService.getPendingAnalysisMemes(botMark, groupId)

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
                        val vectorId = TextImageEncoder.generateVectorId(botMark, groupId, memo.id!!)

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

                        memoService.upsertToVectorStore(updatedMemo)

                        // 更新分析结果（传入当前计数）
                        memoService.updateAnalysis(
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
