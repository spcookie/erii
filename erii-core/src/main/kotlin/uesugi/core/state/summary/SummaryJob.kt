package uesugi.core.state.summary

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.jobrunr.scheduling.JobScheduler
import uesugi.common.BotManage
import uesugi.common.toolkit.logger
import uesugi.core.state.memory.MemoryRepository

/**
 * 摘要任务 - 定时调度对话摘要生成
 *
 * 独立的定时任务，负责生成群组对话摘要（历史压缩）
 */
class SummaryJob(
    val jobScheduler: JobScheduler,
    private val memoryRepository: MemoryRepository,
    private val summaryService: SummaryService
) {

    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()

    /**
     * 开启定时触发
     * 每 30 分钟执行一次摘要生成
     */
    fun openTimingTriggerSignal() {
        jobScheduler.scheduleRecurrently(
            "summary-job",
            "*/5 * * * *",  // 每 5 分钟
            ::doSummaryProcessing
        )
        log.info("Summary task timer started, execution cycle: every 5 minutes")
    }

    /**
     * 执行摘要处理
     * 调度逻辑：遍历所有机器人和群组，调用 Service 生成摘要
     */
    fun doSummaryProcessing() {
        runBlocking {
            if (mutex.tryLock()) {
                try {
                    log.debug("摘要任务开始执行")

                    for (currentBotId in BotManage.getAllBotIds()) {
                        log.debug("开始为机器人 $currentBotId 生成摘要")

                        // 查找需要处理的群组
                        val groups = withContext(Dispatchers.IO) {
                            memoryRepository.findGroupsNeedProcessing(currentBotId)
                        }

                        log.debug("摘要任务发现 ${groups.size} 个群组需要处理")

                        // 使用 coroutineScope 并发处理各群组
                        coroutineScope {
                            for (groupId in groups) {
                                launch(Dispatchers.IO) {
                                    try {
                                        summaryService.processSummaryForGroup(currentBotId, groupId)
                                    } catch (e: Exception) {
                                        log.error("为群组 $groupId 生成摘要失败", e)
                                    }
                                }
                            }
                        }
                    }

                    log.debug("摘要任务执行完成")
                } catch (e: Exception) {
                    log.error("摘要任务执行失败", e)
                } finally {
                    mutex.unlock()
                }
            } else {
                log.debug("摘要任务正在执行中, 跳过本次调度")
            }
        }
    }
}
