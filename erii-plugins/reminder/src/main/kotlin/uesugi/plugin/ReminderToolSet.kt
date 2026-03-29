package uesugi.plugin

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import uesugi.spi.MetaToolSet
import uesugi.spi.MetaToolSet.Companion.meta
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class ReminderToolSet(
    private val store: ReminderStore,
    private val wheel: ReminderWheel
) : MetaToolSet {

    @Tool
    @LLMDescription("添加一个新的定时提醒。参数 content 是提醒内容，triggerTime 是触发时间（如'明天下午3点'、'5分钟后'等自然语言描述），targetUserId 是被提醒的用户 ID（可选），repeatType 是重复类型（none/daily/weekly，可选）")
    suspend fun addReminder(
        @LLMDescription("提醒内容")
        content: String,

        @LLMDescription("触发时间，自然语言描述，如'明天下午3点'、'5分钟后'、'下周一早上9点'等")
        triggerTime: String,

        @LLMDescription("被提醒的用户 ID，可选，默认提醒创建者")
        targetUserId: String? = null,

        @LLMDescription("重复类型：none（不重复）、daily（每天）、weekly（每周），默认 none")
        repeatType: String? = null
    ): String {
        val botId = meta.botId
        val groupId = meta.groupId
        val senderId = meta.senderId

        // 解析时间
        val timestamp = parseTimeString(triggerTime)
            ?: return "无法解析时间：$triggerTime，请使用明确的时间描述，如'明天下午3点'、'5分钟后'等"

        // 解析重复类型
        val repeat = when (repeatType?.lowercase()) {
            "daily" -> RepeatType.DAILY
            "weekly" -> RepeatType.WEEKLY
            else -> RepeatType.NONE
        }

        // 创建提醒任务
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

        // 注册到时间轮（pushTask 内部同步到 store）
        wheel.register(botId, groupId)
        wheel.pushTask(task)

        // 格式化返回时间
        val timeStr = formatTime(timestamp)
        val repeatStr =
            if (repeat != RepeatType.NONE) "，每${if (repeat == RepeatType.DAILY) "天" else "周"}重复" else ""

        return "提醒已设置：$content 将在 $timeStr 触发$repeatStr\n提醒 ID: ${task.reminderId}"
    }

    @Tool
    @LLMDescription("列出当前群组所有活跃的定时提醒")
    suspend fun list_reminders(): String {
        val botId = meta.botId
        val groupId = meta.groupId

        val tasks = store.getAllActiveTasks(botId, groupId)

        if (tasks.isEmpty()) {
            return "当前群组没有活跃的提醒"
        }

        val sb = StringBuilder("当前群组的活跃提醒：\n")
        for ((index, task) in tasks.withIndex()) {
            val targetStr = task.targetUserId?.let { " -> @$it" } ?: ""
            val repeatStr = if (task.repeatType != RepeatType.NONE) {
                " [每${if (task.repeatType == RepeatType.DAILY) "天" else "周"}重复]"
            } else ""
            sb.append("${index + 1}. ${task.content}$targetStr - ${formatTime(task.triggerTime)}$repeatStr\n")
            sb.append("   ID: ${task.reminderId}\n")
        }

        return sb.toString()
    }

    @Tool
    @LLMDescription("修改一个已有的提醒。可修改内容、触发时间或目标用户")
    suspend fun modify_reminder(
        @LLMDescription("要修改的提醒 ID")
        reminderId: String,

        @LLMDescription("新的提醒内容，可选")
        content: String? = null,

        @LLMDescription("新的触发时间，可选")
        triggerTime: String? = null,

        @LLMDescription("新的目标用户 ID，可选")
        targetUserId: String? = null
    ): String {
        val botId = meta.botId
        val groupId = meta.groupId

        val existingTask = store.getTask(botId, groupId, reminderId)
            ?: return "未找到提醒 ID: $reminderId"

        if (existingTask.status == ReminderStatus.DELETED || existingTask.status == ReminderStatus.FIRED) {
            return "该提醒已无法修改（状态：${existingTask.status}）"
        }

        // 解析新时间
        val newTriggerTime = triggerTime?.let { parseTimeString(it) }
            ?: existingTask.triggerTime

        // 更新任务
        val updatedTask = existingTask.copy(
            content = content ?: existingTask.content,
            triggerTime = newTriggerTime,
            targetUserId = targetUserId ?: existingTask.targetUserId
        )

        // 从时间轮移除旧任务，添加新任务（pushTask 内部同步到 store）
        wheel.removeTask(existingTask)
        wheel.pushTask(updatedTask)

        val changes = mutableListOf<String>()
        if (content != null) changes.add("内容")
        if (triggerTime != null) changes.add("时间")
        if (targetUserId != null) changes.add("目标用户")

        return "提醒已修改（${changes.joinToString("、")}）：${updatedTask.content}\n触发时间：${formatTime(updatedTask.triggerTime)}"
    }

    @Tool
    @LLMDescription("删除一个定时提醒")
    suspend fun delete_reminder(
        @LLMDescription("要删除的提醒 ID")
        reminderId: String
    ): String {
        val botId = meta.botId
        val groupId = meta.groupId

        val existingTask = store.getTask(botId, groupId, reminderId)
            ?: return "未找到提醒 ID: $reminderId"

        // 从时间轮移除（removeTask 内部同步删除 store）
        wheel.removeTask(existingTask)

        return "提醒已删除：${existingTask.content}"
    }

    @Tool
    @LLMDescription("手动检查是否有待触发的提醒并立即触发")
    fun checkReminders(): String {
        // JobRunr 调度器每 30 秒自动扫描，这里只是提示用户
        return "提醒检查已就绪，调度器会每 30 秒自动检查待触发的提醒"
    }

    // 时间解析辅助函数
    private fun parseTimeString(timeStr: String): Long? {
        val now = System.currentTimeMillis()
        val lower = timeStr.lowercase().trim()

        // 简单的时间解析
        return when {
            // "X分钟后"
            lower.contains("分钟") || lower.contains("min") -> {
                val minutes = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toLongOrNull() ?: return null
                now + minutes * 60 * 1000
            }
            // "X小时后"
            lower.contains("小时") || lower.contains("hour") || lower.contains("h") -> {
                val hours = Regex("(\\d+)").find(lower)?.groupValues?.get(1)?.toLongOrNull() ?: return null
                now + hours * 60 * 60 * 1000
            }
            // "明天X点"
            lower.contains("明天") -> {
                val timeMatch = Regex("(\\d{1,2})[:点时](\\d{0,2})").find(lower)
                if (timeMatch != null) {
                    val hour = timeMatch.groupValues[1].toInt()
                    val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
                    val tomorrow = now + 24 * 60 * 60 * 1000
                    resetToMidnight(tomorrow) + hour * 60 * 60 * 1000 + minute * 60 * 1000
                } else {
                    // 明天默认早上9点
                    val tomorrow = now + 24 * 60 * 60 * 1000
                    resetToMidnight(tomorrow) + 9 * 60 * 60 * 1000
                }
            }
            // "今天X点"
            lower.contains("今天") -> {
                val timeMatch = Regex("(\\d{1,2})[:点时](\\d{0,2})").find(lower)
                if (timeMatch != null) {
                    val hour = timeMatch.groupValues[1].toInt()
                    val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
                    resetToMidnight(now) + hour * 60 * 60 * 1000 + minute * 60 * 1000
                } else null
            }
            // "下周一" 等
            lower.contains("下周一") || lower.contains("下星期一") -> {
                val daysUntilMonday = ((8 - java.time.LocalDate.now().dayOfWeek.value) % 7).coerceAtLeast(7)
                resetToMidnight(now + daysUntilMonday * 24 * 60 * 60 * 1000) + 9 * 60 * 60 * 1000
            }
            // "周一" / "星期一"
            lower.contains("周一") || lower.contains("星期一") -> {
                val daysUntilTarget = ((8 - java.time.LocalDate.now().dayOfWeek.value) % 7).coerceAtLeast(0)
                resetToMidnight(now + daysUntilTarget * 24 * 60 * 60 * 1000) + 9 * 60 * 60 * 1000
            }
            // 直接是时间格式 "HH:mm" 或 "HH点"
            else -> {
                val timeMatch = Regex("(\\d{1,2})[:点时](\\d{0,2})").find(lower)
                if (timeMatch != null) {
                    val hour = timeMatch.groupValues[1].toInt()
                    val minute = timeMatch.groupValues[2].toIntOrNull() ?: 0
                    val targetTime = resetToMidnight(now) + hour * 60 * 60 * 1000 + minute * 60 * 1000
                    // 如果已经过了，今天+1天
                    if (targetTime <= now) {
                        targetTime + 24 * 60 * 60 * 1000
                    } else {
                        targetTime
                    }
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
        val instant = java.time.Instant.ofEpochMilli(timestamp)
        val localDateTime = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDateTime()
        return "${localDateTime.year}-${
            localDateTime.monthValue.toString().padStart(2, '0')
        }-${localDateTime.dayOfMonth.toString().padStart(2, '0')} ${
            localDateTime.hour.toString().padStart(2, '0')
        }:${localDateTime.minute.toString().padStart(2, '0')}"
    }
}