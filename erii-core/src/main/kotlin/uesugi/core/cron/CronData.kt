package uesugi.core.cron

import kotlinx.serialization.Serializable

@Serializable
enum class RepeatType {
    NONE,
    DAILY,
    WEEKLY
}

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
    val repeatType: RepeatType = RepeatType.NONE,
    val status: CronTaskStatus = CronTaskStatus.ACTIVE,
    val createdAt: Long,
    val firedAt: Long? = null,
    val targetUserId: String? = null,
    val taskType: CronTaskType = CronTaskType.REMINDER,
    val triggerType: TriggerType? = null
) {
    companion object {
        fun nextTriggerTime(task: CronTask, currentTime: Long): Long {
            return when (task.repeatType) {
                RepeatType.NONE -> task.triggerTime
                RepeatType.DAILY -> {
                    var next = task.triggerTime
                    while (next <= currentTime) next += 24 * 60 * 60 * 1000L
                    next
                }

                RepeatType.WEEKLY -> {
                    var next = task.triggerTime
                    while (next <= currentTime) next += 7 * 24 * 60 * 60 * 1000L
                    next
                }
            }
        }
    }
}

data class BotGroupKey(val botId: String, val groupId: String) {
    override fun toString() = "$botId:$groupId"
}
