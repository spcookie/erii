package uesugi.core.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import uesugi.core.cron.*
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CronToolSet(
    private val botId: String,
    private val groupId: String,
    private val senderId: String?,
    private val store: CronStore,
    private val wheel: CronWheel
) : ToolSet {

    @Tool
    @LLMDescription("添加一个新的定时提醒。content 是提醒内容，triggerTime 是触发时间（如'明天下午3点'、'5分钟后'等自然语言描述），targetUserId 是被提醒的用户 ID（可选，不填则@全体），cronExpression 是重复规则（如'0 9 * * *'=每天9点，'0 9 * * 1'=每周一9点，不填则仅触发一次）")
    suspend fun addReminder(
        @LLMDescription("提醒内容")
        content: String,
        @LLMDescription("触发时间，自然语言描述，如'明天下午3点'、'5分钟后'、'下周一早上9点'等")
        triggerTime: String,
        @LLMDescription("被提醒的用户 ID，可选，不填则是全体成员")
        targetUserId: String? = null,
        @LLMDescription("cron 表达式（5字段：分 时 日 月 周），如'0 9 * * *'=每天9点，不填则仅触发一次")
        cronExpression: String? = null
    ): String {
        val timestamp = parseTimeString(triggerTime)
            ?: return "无法解析时间：$triggerTime，请使用明确的时间描述，如'明天下午3点'、'5分钟后'等"

        val task = CronTask(
            taskId = Uuid.random().toHexString(),
            botId = botId,
            groupId = groupId,
            senderId = senderId,
            content = content,
            triggerTime = timestamp,
            cronExpression = cronExpression?.trim()?.takeIf { it.isNotEmpty() },
            status = CronTaskStatus.ACTIVE,
            createdAt = System.currentTimeMillis(),
            targetUserId = targetUserId,
            taskType = CronTaskType.REMINDER
        )

        wheel.pushTask(task)

        val timeStr = formatTime(timestamp)
        val cronStr = task.cronExpression?.let { "，重复规则：$it" } ?: ""

        return "提醒已设置：$content 将在 $timeStr 触发$cronStr\n提醒 ID: ${task.taskId}"
    }

    @Tool
    @LLMDescription("添加一个定时任务触发器，在指定时间自动执行路由或命令。content 是触发内容（routing 类型传入自由文本如'跟群友打招呼'；command 类型传入命令如'/music 周杰伦'），triggerTime 是触发时间，triggerType 是触发类型（routing/command），cronExpression 是重复规则（如'0 9 * * *'=每天9点，不填则仅触发一次）")
    suspend fun addTaskTrigger(
        @LLMDescription("触发内容：routing 时为自由文本，command 时为命令（如'/music 周杰伦'）")
        content: String,
        @LLMDescription("触发时间，自然语言描述，如'明天上午9点'、'5分钟后'")
        triggerTime: String,
        @LLMDescription("触发类型：routing（LLM路由触发）或 command（命令触发）")
        triggerType: String,
        @LLMDescription("cron 表达式（5字段：分 时 日 月 周），如'0 9 * * *'=每天9点，不填则仅触发一次")
        cronExpression: String? = null
    ): String {
        val timestamp = parseTimeString(triggerTime)
            ?: return "无法解析时间：$triggerTime"

        val type = when (triggerType.lowercase()) {
            "routing" -> TriggerType.ROUTING
            "command" -> TriggerType.COMMAND
            else -> return "不支持的触发类型：$triggerType，可选值为 routing 或 command"
        }

        val task = CronTask(
            taskId = Uuid.random().toHexString(),
            botId = botId,
            groupId = groupId,
            senderId = senderId,
            content = content,
            triggerTime = timestamp,
            cronExpression = cronExpression?.trim()?.takeIf { it.isNotEmpty() },
            status = CronTaskStatus.ACTIVE,
            createdAt = System.currentTimeMillis(),
            taskType = CronTaskType.TASK_TRIGGER,
            triggerType = type
        )

        wheel.pushTask(task)

        val timeStr = formatTime(timestamp)
        val typeLabel = if (type == TriggerType.ROUTING) "路由" else "命令"
        val cronStr = task.cronExpression?.let { "，重复规则：$it" } ?: ""

        return "任务触发器已设置：[$typeLabel] $content 将在 $timeStr 触发$cronStr\n任务 ID: ${task.taskId}"
    }

    @Tool
    @LLMDescription("列出当前群组所有活跃的定时任务（包含提醒和任务触发器）")
    suspend fun listCronTasks(): String {
        val tasks = store.getAllActiveTasks(botId, groupId)

        if (tasks.isEmpty()) return "当前群组没有活跃的定时任务"

        val reminders = tasks.filter { it.taskType == CronTaskType.REMINDER }
        val triggers = tasks.filter { it.taskType == CronTaskType.TASK_TRIGGER }

        return buildString {
            if (reminders.isNotEmpty()) {
                appendLine("【提醒】")
                for ((index, task) in reminders.withIndex()) {
                    appendTaskInfo(index, task)
                }
            }
            if (triggers.isNotEmpty()) {
                if (reminders.isNotEmpty()) appendLine()
                appendLine("【任务触发器】")
                for ((index, task) in triggers.withIndex()) {
                    appendTaskInfo(index, task)
                }
            }
        }.trim()
    }

    private fun StringBuilder.appendTaskInfo(index: Int, task: CronTask) {
        val targetStr = task.targetUserId?.let { " -> @$it" } ?: ""
        val cronStr = task.cronExpression?.let { " [cron: $it]" } ?: ""
        val typeStr = if (task.taskType == CronTaskType.TASK_TRIGGER) {
            " [${if (task.triggerType == TriggerType.ROUTING) "路由" else "命令"}]"
        } else ""

        appendLine("${index + 1}. ${task.content}$targetStr - ${formatTime(task.triggerTime)}$cronStr$typeStr")
        appendLine("   ID: ${task.taskId}")
    }

    @Tool
    @LLMDescription("修改一个已有的定时任务（提醒或任务触发器）。可修改内容、触发时间、目标用户或 cron 表达式")
    suspend fun modifyCronTask(
        @LLMDescription("要修改的任务 ID")
        taskId: String,
        @LLMDescription("新的内容，可选")
        content: String? = null,
        @LLMDescription("新的触发时间，可选")
        triggerTime: String? = null,
        @LLMDescription("新的目标用户 ID，可选")
        targetUserId: String? = null,
        @LLMDescription("新的 cron 表达式，可选")
        cronExpression: String? = null
    ): String {
        val existingTask = store.getTask(botId, groupId, taskId)
            ?: return "未找到任务 ID: $taskId"

        if (existingTask.senderId != null && existingTask.senderId != senderId) {
            return "无权限修改该任务，只有创建者才能修改"
        }

        if (existingTask.status != CronTaskStatus.ACTIVE) {
            return "该任务已无法修改（状态：${existingTask.status}）"
        }

        val newTriggerTime = triggerTime?.let { parseTimeString(it) } ?: existingTask.triggerTime

        val updatedTask = existingTask.copy(
            content = content ?: existingTask.content,
            triggerTime = newTriggerTime,
            targetUserId = targetUserId ?: existingTask.targetUserId,
            cronExpression = cronExpression?.trim()?.takeIf { it.isNotEmpty() } ?: existingTask.cronExpression
        )

        wheel.removeTask(existingTask)
        wheel.pushTask(updatedTask)

        val changes = buildList {
            if (content != null) add("内容")
            if (triggerTime != null) add("时间")
            if (targetUserId != null) add("目标用户")
            if (cronExpression != null) add("cron表达式")
        }

        val typeLabel = if (existingTask.taskType == CronTaskType.TASK_TRIGGER) "任务触发器" else "提醒"
        return "$typeLabel 已修改（${changes.joinToString("、")}）：${updatedTask.content}\n触发时间：${
            formatTime(
                updatedTask.triggerTime
            )
        }"
    }

    @Tool
    @LLMDescription("删除一个定时任务（提醒或任务触发器）")
    suspend fun deleteCronTask(
        @LLMDescription("要删除的任务 ID")
        taskId: String
    ): String {
        val existingTask = store.getTask(botId, groupId, taskId)
            ?: return "未找到任务 ID: $taskId"

        if (existingTask.senderId != null && existingTask.senderId != senderId) {
            return "无权限删除该任务，只有创建者才能删除"
        }

        wheel.removeTask(existingTask)

        val typeLabel = if (existingTask.taskType == CronTaskType.TASK_TRIGGER) "任务触发器" else "提醒"
        return "$typeLabel 已删除：${existingTask.content}"
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
