package uesugi.plugin

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uesugi.common.PSFeature
import uesugi.spi.AgentSender
import uesugi.spi.Feature
import uesugi.spi.PluginContext
import uesugi.spi.Scheduler
import java.util.*
import kotlin.time.Duration.Companion.seconds

class ReminderScheduler(
    private val scheduler: Scheduler,
    private val wheel: ReminderWheel,
    private val context: PluginContext
) {
    private val logger = KotlinLogging.logger {}
    private val store = ReminderStoreImpl(context.kv)

    companion object {
        private const val SCAN_JOB_ID = "reminder-scan"
        private const val SCAN_INTERVAL_SECONDS = 30
    }

    fun start() {
        // 每 30 秒扫描一次
        scheduler.scheduleRecurrently(SCAN_JOB_ID, SCAN_INTERVAL_SECONDS.seconds) {
            scanDueReminders()
        }
        logger.info { "ReminderScheduler started, scanning every $SCAN_INTERVAL_SECONDS seconds" }
    }

    fun stop() {
        scheduler.cancel(SCAN_JOB_ID)
        logger.info { "ReminderScheduler stopped" }
    }

    private fun scanDueReminders() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val keys = wheel.getRegisteredKeys()

                for (key in keys) {
                    scanDueTasksForGroup(key, now)
                }
            } catch (e: Exception) {
                logger.error(e) { "Error scanning due reminders" }
            }
        }
    }

    private suspend fun scanDueTasksForGroup(key: BotGroupKey, now: Long) {
        try {
            // 确保 wheel 已注册该 key
            wheel.register(key.botId, key.groupId)

            // 先从 store 加载该群组的待触发任务（处理 app 重启后 wheel 为空的情况）
            store.getAllActiveTasks(key.botId, key.groupId)
                .filter { it.triggerTime <= now }
                .forEach { task -> wheel.pushTask(task) }

            // 从时间轮获取到期的任务
            val dueTasks = wheel.getAndClearDueTasks(key.botId, key.groupId, now)

            for (task in dueTasks) {
                fireReminder(task)
            }
        } catch (e: Exception) {
            logger.error(e) { "Error scanning tasks for ${key.botId}:${key.groupId}" }
        }
    }

    private suspend fun fireReminder(task: ReminderTask) {
        try {
            logger.info { "Firing reminder ${task.reminderId}: ${task.content}" }

            // 计算更新后的任务
            val updatedTask = if (task.repeatType == RepeatType.NONE) {
                task.copy(
                    status = ReminderStatus.FIRED,
                    firedAt = System.currentTimeMillis()
                )
            } else {
                // 计算下次触发时间
                val nextTrigger = ReminderTask.nextTriggerTime(task, System.currentTimeMillis())
                task.copy(triggerTime = nextTrigger)
            }

            // 如果是重复任务，重新加入时间轮（pushTask 会同步到 store）；否则删除
            if (task.repeatType != RepeatType.NONE) {
                wheel.pushTask(updatedTask)
            } else {
                store.deleteTask(task.botId, task.groupId, task.reminderId)
            }

            // 发送提醒消息
            val message = buildReminderMessage(task)
            sendReminderMessage(task, message)

        } catch (e: Exception) {
            logger.error(e) { "Error firing reminder ${task.reminderId}" }
        }
    }

    private fun buildReminderMessage(task: ReminderTask): String {
        val targetMention = task.targetUserId?.let { "@$it " } ?: ""
        return "$targetMention${task.content}"
    }

    private fun sendReminderMessage(task: ReminderTask, message: String) {
        val agentSender = ServiceLoader.load(AgentSender::class.java).firstOrNull()
            ?: run {
                logger.warn { "No AgentSender found" }
                return
            }

        agentSender.sendAgent(
            botId = task.botId,
            groupId = task.groupId,
            input = message,
            config = Feature(PSFeature.GRAB or PSFeature.CHAT_URGENT)
        )
    }

    fun enqueueImmediateScan() {
        // 手动触发一次扫描
        scanDueReminders()
    }
}