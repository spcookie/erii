package uesugi.core.reminder

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.buildMessageChain
import org.jobrunr.scheduling.JobScheduler
import uesugi.common.BotManage
import uesugi.common.event.PSFeature
import uesugi.spi.Feature
import uesugi.spi.sendAgent
import kotlin.time.Duration.Companion.seconds

class ReminderService(private val jobScheduler: JobScheduler) {

    private val log = KotlinLogging.logger {}

    val store = ReminderStore()
    val wheel = ReminderWheel(store)

    companion object {
        private const val SCAN_JOB_ID = "core.reminder-scan"
        private const val SCAN_INTERVAL_SECONDS = 30
    }

    fun start() {
        runBlocking { wheel.init() }

        // 立即扫描一次，处理重启期间到期的任务
        scanDueReminders()

        jobScheduler.scheduleRecurrently(
            SCAN_JOB_ID,
            SCAN_INTERVAL_SECONDS.seconds.toJavaDuration(),
            ::scanDueReminders
        )

        log.info { "ReminderService started, scanning every $SCAN_INTERVAL_SECONDS seconds" }
    }

    fun stop() {
        log.info { "ReminderService stopped" }
    }

    private fun scanDueReminders() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                for (key in wheel.getRegisteredKeys()) {
                    scanDueTasksForGroup(key, now)
                }
            } catch (e: Exception) {
                log.error(e) { "Error scanning due reminders" }
            }
        }
    }

    private suspend fun scanDueTasksForGroup(key: BotGroupKey, now: Long) {
        try {
            val dueTasks = wheel.getAndClearDueTasks(key.botId, key.groupId, now)
            for (task in dueTasks) {
                fireReminder(task)
            }
        } catch (e: Exception) {
            log.error(e) { "Error scanning tasks for $key" }
        }
    }

    private suspend fun fireReminder(task: ReminderTask) {
        try {
            log.info { "Firing reminder ${task.reminderId}: ${task.content}" }

            if (task.repeatType == RepeatType.NONE) {
                // 非重复任务：标记为已触发
                store.deleteTask(task.botId, task.groupId, task.reminderId)
            } else {
                // 重复任务：计算下次触发时间并重新入队
                val nextTrigger = ReminderTask.nextTriggerTime(task, System.currentTimeMillis())
                wheel.pushTask(task.copy(triggerTime = nextTrigger))
            }

            sendReminderMessage(task)
        } catch (e: Exception) {
            log.error(e) { "Error firing reminder ${task.reminderId}" }
        }
    }

    private fun sendReminderMessage(task: ReminderTask) {
        val targetMention = task.targetUserId?.let { "@$it " } ?: ""
        sendAgent(
            task.botId,
            task.groupId,
            """
                提醒时间到了，你需要在群里发送消息@用户进行提醒。

                要求：
                1. 语气自然、像人聊天
                2. 简短，不要啰嗦
                3. 不要使用"提醒到了"、"请注意"等正式表达
                4. 不要像系统通知
                5. 可以稍微口语一点，但不要过度发挥

                用户：${targetMention}
                提醒内容：${task.content}
            """.trimIndent(),
            Feature(PSFeature.CHAT_URGENT or PSFeature.GRAB or PSFeature.FALLBACK)
        ) {
            callFallback {
                val messages = buildMessageChain {
                    task.targetUserId?.let { +At(it.toLong()) }
                    +task.content
                }
                BotManage.getBot(task.botId)
                    .refBot
                    .getGroupOrFail(task.groupId.toLong())
                    .sendMessage(messages)
            }
            CoroutineScope(kotlinx.coroutines.Job())
        }
    }
}

private fun kotlin.time.Duration.toJavaDuration(): java.time.Duration =
    java.time.Duration.ofMillis(this.inWholeMilliseconds)
