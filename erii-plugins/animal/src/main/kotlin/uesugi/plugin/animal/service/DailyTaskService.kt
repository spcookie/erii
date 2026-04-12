package uesugi.plugin.animal.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import uesugi.plugin.animal.store.AnimalStore
import uesugi.spi.Scheduler

class DailyTaskService(
    private val store: AnimalStore,
    private val service: AnimalService,
    private val scheduler: Scheduler
) {
    private val log = KotlinLogging.logger {}

    companion object {
        private const val DAILY_RESET_JOB_ID = "animal-daily-reset"
        private const val DAILY_RESET_CRON = "0 0 * * *"  // 每日0点
    }

    fun startDailyTasks() {
        // 每日0点重置消息计数并检查未发言用户
        scheduler.scheduleRecurrently(DAILY_RESET_JOB_ID, DAILY_RESET_CRON) {
            log.info { "Running daily animal task" }
            try {
                runBlocking {
                    service.resetDailyTasks()
                }
            } catch (e: Exception) {
                log.error(e) { "Error in daily animal task" }
            }
        }
        log.info { "Animal daily tasks scheduled with cron: $DAILY_RESET_CRON" }
    }

    fun stopDailyTasks() {
        scheduler.cancel(DAILY_RESET_JOB_ID)
        log.info { "Animal daily tasks stopped" }
    }
}
