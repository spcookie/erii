package uesugi.core.state.emotion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.jobrunr.scheduling.JobScheduler
import uesugi.common.BotManage
import uesugi.common.toolkit.logger
import uesugi.core.component.usage.UsageContext

/**
 * 情绪任务 - 定时调度情绪分析和衰减
 *
 * 仅负责调度和组合逻辑，业务逻辑封装在 EmotionService 中
 */
class EmotionJob(
    val jobScheduler: JobScheduler,
    private val emotionRepository: EmotionRepository,
    private val emotionService: EmotionService
) {

    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()

    /**
     * 开启定时触发
     * 每 2 分钟执行一次情绪分析
     */
    fun openTimingTriggerSignal() {
        jobScheduler.scheduleRecurrently(
            "emotion-job",
            "*/2 * * * *",
            ::doAnalysis
        )
        log.info("Emotional task timer has started, execution cycle: every 2 minutes")
    }

    /**
     * 执行情绪分析
     * 调度逻辑：遍历所有机器人和群组，调用 Service 处理
     *
     * 注意：JobRunr 调用此方法的线程不应阻塞，实际工作在独立协程中异步执行
     */
    fun doAnalysis() {
        if (!mutex.tryLock()) {
            log.debug("情绪任务正在执行中, 跳过本次调度")
            return
        }

        try {
            runBlocking {
                try {
                    log.debug("情绪任务开始执行")
                    for (currentBotId in BotManage.getAllBotIds()) {
                        log.debug("开始执行的情绪分析, botId=$currentBotId")

                        // 1. 查找需要分析的群组
                        val groups = withContext(Dispatchers.IO) {
                            emotionRepository.findGroupsNeedAnalysis(currentBotId)
                        }
                        log.debug("情绪任务发现 ${groups.size} 个群组有新消息需要分析")

                        // 2. 对每个群组执行情绪分析（单群失败不影响其他群）
                        for (group in groups) {
                            try {
                                UsageContext.withUsage(currentBotId, group) {
                                    emotionService.analyzeGroupEmotion(currentBotId, group)
                                }
                            } catch (e: Exception) {
                                log.error("Emotional analysis failed, botId=$currentBotId, group=$group", e)
                            }
                        }

                        // 3. 查找需要衰减的群组
                        val decayGroups = withContext(Dispatchers.IO) {
                            emotionRepository.findGroupsNotNeedAnalysis(currentBotId, groups)
                        }
                        log.debug("情绪任务发现 ${decayGroups.size} 个群组需要执行情绪衰减")

                        // 4. 对每个群组执行情绪衰减
                        for (group in decayGroups) {
                            try {
                                emotionService.decayGroupEmotion(currentBotId, group)
                            } catch (e: Exception) {
                                log.error("Emotional decline fails, botId=$currentBotId, group=$group", e)
                            }
                        }
                    }

                    log.debug("情绪任务执行完成")
                } catch (e: Exception) {
                    log.error("Emotional task execution fails", e)
                }
            }
        } finally {
            mutex.unlock()
        }
    }
}
