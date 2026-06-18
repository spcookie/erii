package uesugi.core.state.summary

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jobrunr.scheduling.JobScheduler
import uesugi.common.BotManage
import uesugi.common.toolkit.logger
import uesugi.core.state.summary.SummaryJob.Companion.SUMMARY_RETENTION_DAYS
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

/**
 * 摘要任务 - 定时调度对话摘要生成
 *
 * 独立的定时任务，负责生成群组对话摘要（历史压缩）
 */
class SummaryJob(
    val jobScheduler: JobScheduler,
    private val summaryService: SummaryService,
    private val summaryRepository: SummaryRepository
) {

    companion object {
        private val log = logger()
        private const val SUMMARY_RETENTION_DAYS = 7L
    }

    private val mutex = Mutex()
    private val cleanupMutex = Mutex()

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
        jobScheduler.scheduleRecurrently(
            "summary-cleanup-job",
            "0 3 * * *",  // 每日 03:00
            ::doSummaryCleanup
        )
        log.info("Summary task timer started, generation: every 5 minutes, cleanup: daily 03:00")
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
                            summaryRepository.findGroupsNeedProcessing(currentBotId)
                        }

                        log.debug("摘要任务发现 ${groups.size} 个群组需要处理")

                        // 使用 coroutineScope 并发处理各群组
                        coroutineScope {
                            for (groupId in groups) {
                                launch(Dispatchers.IO) {
                                    try {
                                        summaryService.processSummaryForGroup(currentBotId, groupId)
                                    } catch (e: Exception) {
                                        log.error("Failed to generate a summary for a group $groupId", e)
                                    }
                                }
                            }
                        }
                    }

                    log.debug("摘要任务执行完成")
                } catch (e: Exception) {
                    log.error("Summary task execution failed", e)
                } finally {
                    mutex.unlock()
                }
            } else {
                log.debug("摘要任务正在执行中, 跳过本次调度")
            }
        }
    }

    /**
     * 执行摘要清理
     * 删除 createdAt 早于 [SUMMARY_RETENTION_DAYS] 天的记录
     */
    @OptIn(ExperimentalTime::class)
    fun doSummaryCleanup() {
        runBlocking {
            if (cleanupMutex.tryLock()) {
                try {
                    val cutoff = Clock.System.now()
                        .minus(SUMMARY_RETENTION_DAYS.days)
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                    log.debug("摘要清理任务开始执行, cutoff={}", cutoff)

                    val deleted = withContext(Dispatchers.IO) {
                        summaryRepository.deleteSummariesBefore(cutoff)
                    }

                    if (deleted > 0) {
                        log.info("Summary cleanup task completed, $deleted expired records deleted (cutoff=$cutoff)")
                    } else {
                        log.debug("摘要清理任务完成, 无过期记录")
                    }
                } catch (e: Exception) {
                    log.error("Summary cleanup task execution failed", e)
                } finally {
                    cleanupMutex.unlock()
                }
            } else {
                log.debug("摘要清理任务正在执行中, 跳过本次调度")
            }
        }
    }
}
