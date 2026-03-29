package uesugi.plugin

import kotlinx.serialization.Serializable

@Serializable
enum class RepeatType {
    NONE,
    DAILY,
    WEEKLY
}

@Serializable
enum class ReminderStatus {
    ACTIVE,
    FIRED,
    DELETED
}

@Serializable
data class ReminderTask(
    val reminderId: String,
    val botId: String,
    val groupId: String,
    val senderId: String?,
    val content: String,
    val triggerTime: Long,
    val repeatType: RepeatType = RepeatType.NONE,
    val status: ReminderStatus = ReminderStatus.ACTIVE,
    val createdAt: Long,
    val firedAt: Long? = null,
    val targetUserId: String? = null
) {
    companion object {
        fun nextTriggerTime(task: ReminderTask, currentTime: Long): Long {
            return when (task.repeatType) {
                RepeatType.NONE -> task.triggerTime
                RepeatType.DAILY -> {
                    var next = task.triggerTime
                    while (next <= currentTime) {
                        next += 24 * 60 * 60 * 1000L
                    }
                    next
                }

                RepeatType.WEEKLY -> {
                    var next = task.triggerTime
                    while (next <= currentTime) {
                        next += 7 * 24 * 60 * 60 * 1000L
                    }
                    next
                }
            }
        }
    }
}

data class BotGroupKey(
    val botId: String,
    val groupId: String
) {
    override fun toString() = "$botId:$groupId"
}