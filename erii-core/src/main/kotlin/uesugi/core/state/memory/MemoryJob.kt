package uesugi.core.state.memory

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.jobrunr.scheduling.JobScheduler
import uesugi.BotManage
import uesugi.common.logger

/**
 * 记忆任务 - 定时调度记忆处理
 *
 * 仅负责调度和组合逻辑，业务逻辑封装在 MemoryService 中
 */
class MemoryJob(
    val jobScheduler: JobScheduler,
    private val memoryRepository: MemoryRepository,
    private val memoryService: MemoryService
) {

    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()

    /**
     * 开启定时触发
     * 每 5 分钟执行一次记忆处理
     */
    fun openTimingTriggerSignal() {
        jobScheduler.scheduleRecurrently(
            "memory-job",
            "*/5 * * * *",  // 每 5 分钟
            ::doMemoryProcessing
        )
        log.info("记忆任务定时器已启动, 执行周期: 每5分钟")
    }

    /**
     * 执行记忆处理
     * 调度逻辑：遍历所有机器人和群组，调用 Service 处理
     * 支持群组并发处理
     */
    fun doMemoryProcessing() {
        runBlocking {
            if (mutex.tryLock()) {
                try {
                    log.debug("记忆任务开始执行")

                    for (currentBotId in BotManage.getAllBotIds()) {
                        log.debug("开始处理机器人 $currentBotId 的记忆")

                        // 查找需要处理的群组
                        val groups = withContext(Dispatchers.IO) {
                            memoryRepository.findGroupsNeedProcessing(currentBotId)
                        }

                        log.debug("记忆任务发现 ${groups.size} 个群组需要处理")

                        // 使用 coroutineScope 并发处理各群组
                        coroutineScope {
                            for (groupId in groups) {
                                launch(Dispatchers.IO) {
                                    try {
                                        memoryService.processGroupMemory(currentBotId, groupId)
                                    } catch (e: Exception) {
                                        log.error("处理群组 $groupId 记忆失败", e)
                                    }
                                }
                            }
                        }
                    }

                    log.debug("记忆任务执行完成")
                } catch (e: Exception) {
                    log.error("记忆任务执行失败", e)
                } finally {
                    mutex.unlock()
                }
            } else {
                log.debug("记忆任务正在执行中, 跳过本次调度")
            }
        }
    }
}
