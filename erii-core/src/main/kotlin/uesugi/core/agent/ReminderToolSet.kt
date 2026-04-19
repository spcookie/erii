package uesugi.core.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import uesugi.core.reminder.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ReminderToolSet(
    private val botId: String,
    private val groupId: String,
    private val senderId: String?,
    private val store: ReminderStore,
    private val wheel: ReminderWheel
) : ToolSet {

    @Tool
    @LLMDescription("添加一个新的定时提醒。参数 content 是提醒内容，triggerTime 是触发时间（如'明天下午3点'、'5分钟后'等自然语言描述），targetUserId 是被提醒的用户 ID，可选，不填则是全体成员，repeatType 是重复类型（none/daily/weekly）")
    suspend fun addReminder(
        @LLMDescription("提醒内容")
        content: String,
        @LLMDescription("触发时间，自然语言描述，如'明天下午3点'、'5分钟后'、'下周一早上9点'等")
        triggerTime: String,
        @LLMDescription("被提醒的用户 ID，可选，不填则是全体成员")
        targetUserId: String? = null,
        @LLMDescription("重复类型：none（不重复）、daily（每天）、weekly（每周），默认 none")
        repeatType: String? = null
    ): String {
        val timestamp = parseTimeString(triggerTime)
            ?: return "无法解析时间：$triggerTime，请使用明确的时间描述，如'明天下午3点'、'5分钟后'等"

        val repeat = when (repeatType?.lowercase()) {
            "daily" -> RepeatType.DAILY
            "weekly" -> RepeatType.WEEKLY
            else -> RepeatType.NONE
        }

        val task = ReminderTask(
            reminderId = Uuid.random().toHexString(),
            botId = botId,
            groupId = groupId,
            senderId = senderId,
            content = content,
            triggerTime = timestamp,
            repeatType = repeat,
            status = ReminderStatus.ACTIVE,
            createdAt = System.currentTimeMillis(),
            targetUserId = targetUserId
        )

        wheel.pushTask(task)

        val timeStr = formatTime(timestamp)
        val repeatStr = if (repeat != RepeatType.NONE) {
            "，每${if (repeat == RepeatType.DAILY) "天" else "周"}重复"
        } else ""

        return "提醒已设置：$content 将在 $timeStr 触发$repeatStr\n提醒 ID: ${task.reminderId}"
    }

    @Tool
    @LLMDescription("列出当前群组所有活跃的定时提醒")
    suspend fun listReminders(): String {
        val tasks = store.getAllActiveTasks(botId, groupId)

        if (tasks.isEmpty()) return "当前群组没有活跃的提醒"

        return buildString {
            appendLine("当前群组的活跃提醒：")
            for ((index, task) in tasks.withIndex()) {
                val targetStr = task.targetUserId?.let { " -> @$it" } ?: ""
                val repeatStr = if (task.repeatType != RepeatType.NONE) {
                    " [每${if (task.repeatType == RepeatType.DAILY) "天" else "周"}重复]"
                } else ""
                appendLine("${index + 1}. ${task.content}$targetStr - ${formatTime(task.triggerTime)}$repeatStr")
                appendLine("   ID: ${task.reminderId}")
            }
        }.trim()
    }

    @Tool
    @LLMDescription("修改一个已有的提醒。可修改内容、触发时间或目标用户")
    suspend fun modifyReminder(
        @LLMDescription("要修改的提醒 ID")
        reminderId: String,
        @LLMDescription("新的提醒内容，可选")
        content: String? = null,
        @LLMDescription("新的触发时间，可选")
        triggerTime: String? = null,
        @LLMDescription("新的目标用户 ID，可选")
        targetUserId: String? = null
    ): String {
        val existingTask = store.getTask(botId, groupId, reminderId)
            ?: return "未找到提醒 ID: $reminderId"

        if (existingTask.status != ReminderStatus.ACTIVE) {
            return "该提醒已无法修改（状态：${existingTask.status}）"
        }

        val newTriggerTime = triggerTime?.let { parseTimeString(it) } ?: existingTask.triggerTime

        val updatedTask = existingTask.copy(
            content = content ?: existingTask.content,
            triggerTime = newTriggerTime,
            targetUserId = targetUserId ?: existingTask.targetUserId
        )

        wheel.removeTask(existingTask)
        wheel.pushTask(updatedTask)

        val changes = buildList {
            if (content != null) add("内容")
            if (triggerTime != null) add("时间")
            if (targetUserId != null) add("目标用户")
        }

        return "提醒已修改（${changes.joinToString("、")}）：${updatedTask.content}\n触发时间：${formatTime(updatedTask.triggerTime)}"
    }

    @Tool
    @LLMDescription("删除一个定时提醒")
    suspend fun deleteReminder(
        @LLMDescription("要删除的提醒 ID")
        reminderId: String
    ): String {
        val existingTask = store.getTask(botId, groupId, reminderId)
            ?: return "未找到提醒 ID: $reminderId"

        wheel.removeTask(existingTask)

        return "提醒已删除：${existingTask.content}"
    }

    private fun parseTimeString(timeStr: String): Long? {
        val now = System.currentTimeMillis()
        val lower = timeStr.lowercase().trim()

        return when {
            lower.contains("分钟") || lower.contains("min") -> {
                val minutes = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toLongOrNull() ?: return null
                now + minutes * 60 * 1000
            }
            lower.contains("小时") || lower.contains("hour") -> {
                val hours = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toLongOrNull() ?: return null
                now + hours * 60 * 60 * 1000
            }
            lower.contains("明天") -> {
                val timeMatch = Regex("(\\d{1,2})[:点时](\\d{0,2})").find(lower)
                if (timeMatch != null) {
                    val hour = timeMatch.groupValues[1].toInt()
                    val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
                    resetToMidnight(now + 24 * 60 * 60 * 1000) + hour * 60 * 60 * 1000 + minute * 60 * 1000
                } else {
                    resetToMidnight(now + 24 * 60 * 60 * 1000) + 9 * 60 * 60 * 1000
                }
            }
            lower.contains("今天") -> {
                val timeMatch = Regex("(\\d{1,2})[:点时](\\d{0,2})").find(lower)
                if (timeMatch != null) {
                    val hour = timeMatch.groupValues[1].toInt()
                    val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
                    resetToMidnight(now) + hour * 60 * 60 * 1000 + minute * 60 * 1000
                } else null
            }
            lower.contains("下周一") || lower.contains("下星期一") -> {
                val daysUntil = ((8 - java.time.LocalDate.now().dayOfWeek.value) % 7).coerceAtLeast(7)
                resetToMidnight(now + daysUntil * 24 * 60 * 60 * 1000) + 9 * 60 * 60 * 1000
            }
            lower.contains("周一") || lower.contains("星期一") -> {
                val daysUntil = ((8 - java.time.LocalDate.now().dayOfWeek.value) % 7).coerceAtLeast(0)
                resetToMidnight(now + daysUntil * 24 * 60 * 60 * 1000) + 9 * 60 * 60 * 1000
            }
            else -> {
                val timeMatch = Regex("(\\d{1,2})[:点时](\\d{0,2})").find(lower)
                if (timeMatch != null) {
                    val hour = timeMatch.groupValues[1].toInt()
                    val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
                    val targetTime = resetToMidnight(now) + hour * 60 * 60 * 1000 + minute * 60 * 1000
                    if (targetTime <= now) targetTime + 24 * 60 * 60 * 1000 else targetTime
                } else null
            }
        }
    }

    private fun resetToMidnight(timestamp: Long): Long {
        val instant = java.time.Instant.ofEpochMilli(timestamp)
        val localDate = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        return localDate.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }

    private fun formatTime(timestamp: Long): String {
        val ldt = java.time.Instant.ofEpochMilli(timestamp)
            .atZone(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
        return "${ldt.year}-${ldt.monthValue.toString().padStart(2, '0')}-${
            ldt.dayOfMonth.toString().padStart(2, '0')
        } ${ldt.hour.toString().padStart(2, '0')}:${ldt.minute.toString().padStart(2, '0')}"
    }
}
