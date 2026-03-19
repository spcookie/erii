package uesugi.core.state.emotion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.jobrunr.scheduling.JobScheduler
import uesugi.BotManage
import uesugi.common.logger

/**
 * 情绪任务 - 定时调度情绪分析和衰减
 *
 * 仅负责调度和组合逻辑，业务逻辑封装在 EmotionService 中
 */
class EmotionJob(
    val jobScheduler: JobScheduler
) {

    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()
    private val emotionRepository = EmotionRepository()
    private val emotionService = EmotionService(emotionRepository)

    /**
     * 开启定时触发
     * 每分钟执行一次情绪分析
     */
    fun openTimingTriggerSignal() {
        jobScheduler.scheduleRecurrently(
            "emotion-job",
            "* * * * *",  // 每分钟
            ::doAnalysis
        )
        log.info("情绪任务定时器已启动, 执行周期: 每分钟")
    }

    /**
     * 执行情绪分析
     * 调度逻辑：遍历所有机器人和群组，调用 Service 处理
     */
    fun doAnalysis() {
        runBlocking {
            if (mutex.tryLock()) {
                try {
                    log.debug("情绪任务开始执行")
                    for (currentBotId in BotManage.getAllBotIds()) {
                        log.debug("开始执行的情绪分析, botId=$currentBotId")

                        // 1. 查找需要分析的群组
                        val groups = withContext(Dispatchers.IO) {
                            emotionRepository.findGroupsNeedAnalysis(currentBotId)
                        }
                        log.debug("情绪任务发现 ${groups.size} 个群组有新消息需要分析")

                        // 2. 对每个群组执行情绪分析
                        for (group in groups) {
                            emotionService.analyzeGroupEmotion(
                                currentBotId,
                                group,
                                groupSize = 10,
                                adminPresent = false
                            )
                        }

                        // 3. 查找需要衰减的群组
                        val decayGroups = withContext(Dispatchers.IO) {
                            emotionRepository.findGroupsNotNeedAnalysis(currentBotId, groups)
                        }
                        log.debug("情绪任务发现 ${decayGroups.size} 个群组需要执行情绪衰减")

                        // 4. 对每个群组执行情绪衰减
                        for (group in decayGroups) {
                            emotionService.decayGroupEmotion(
                                currentBotId,
                                group,
                                groupSize = 10,
                                adminPresent = false
                            )
                        }
                    }

                    log.debug("情绪任务执行完成")
                } catch (e: Exception) {
                    log.error("情绪任务执行失败", e)
                } finally {
                    mutex.unlock()
                }
            } else {
                log.debug("情绪任务正在执行中, 跳过本次调度")
            }
        }
    }
}
