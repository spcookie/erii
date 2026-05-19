package uesugi.core.cron

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import org.jobrunr.scheduling.cron.CronExpression
import java.time.Instant
import java.time.ZoneId

@Serializable
enum class CronTaskStatus {
    ACTIVE,
    FIRED,
    DELETED
}

@Serializable
enum class CronTaskType {
    REMINDER,
    TASK_TRIGGER
}

@Serializable
enum class TriggerType {
    ROUTING,
    COMMAND
}

@Serializable
data class CronTask(
    val taskId: String,
    val botId: String,
    val groupId: String,
    val senderId: String?,
    val content: String,
    val triggerTime: Long,
    val cronExpression: String? = null,
    @EncodeDefault val status: CronTaskStatus = CronTaskStatus.ACTIVE,
    val createdAt: Long,
    val firedAt: Long? = null,
    val targetUserId: String? = null,
    @EncodeDefault val taskType: CronTaskType = CronTaskType.REMINDER,
    val triggerType: TriggerType? = null
) {
    companion object {
        internal const val MAX_LOOKAHEAD_MS = 2L * 365 * 24 * 60 * 60 * 1000 // 2 years

        fun nextTriggerTime(task: CronTask, currentTime: Long): Long {
            val cron = task.cronExpression ?: return task.triggerTime
            return try {
                val expr = CronExpression(cron)
                val now = Instant.ofEpochMilli(currentTime)
                val next = expr.next(now, now, ZoneId.systemDefault())
                next?.toEpochMilli() ?: (currentTime + MAX_LOOKAHEAD_MS)
            } catch (_: Exception) {
                currentTime + MAX_LOOKAHEAD_MS
            }
        }
    }
}

data class BotGroupKey(val botId: String, val groupId: String) {
    override fun toString() = "$botId:$groupId"
}
