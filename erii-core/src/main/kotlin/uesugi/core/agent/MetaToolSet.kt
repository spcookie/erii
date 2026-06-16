package uesugi.core.agent

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

object MetaToolSet : ToolSet {
    @Tool
    @LLMDescription("获取当前精确时间元数据，包括时、分、秒、星期、时段、Unix时间戳等。当你需要知道当前具体时间时调用此工具。")
    fun getCurrentTime(): String {
        val instant = Clock.System.now()
        val localDateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        val hour = localDateTime.hour
        val minute = localDateTime.minute
        val second = localDateTime.second

        return buildString {
            appendLine("${hour}时${minute}分${second}秒")
            appendLine("时段: ${periodOf(hour)}")
            appendLine("星期: ${localDateTime.dayOfWeek.name}")
            appendLine("时区: ${TimeZone.currentSystemDefault().id}")
            append("Unix时间戳: ${instant.epochSeconds}")
        }
    }

    private fun periodOf(hour: Int): String = when (hour) {
        in 0..5 -> "凌晨"
        in 6..8 -> "早晨"
        in 9..11 -> "上午"
        in 12..13 -> "中午"
        in 14..17 -> "下午"
        in 18..21 -> "傍晚"
        else -> "深夜"
    }
}