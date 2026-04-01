package uesugi.core.agent

import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.reflect.ToolSet
import uesugi.common.BotManage
import uesugi.common.ChatToolSet
import uesugi.common.ProactiveSpeakEvent
import uesugi.common.WebSearchTool

data class ToolEnv(
    val chatToolSet: ChatToolSet,
    val webSearch: Boolean,
    val toolSetBuilder: ((ChatToolSet) -> List<ToolSet>)?,
    val ruleToolSet: RuleToolSet?
)

context(env: ToolEnv)
fun baseTools() = buildList {
    addAll(env.chatToolSet.asTools())
    addAll(SilentToolSet.asTools())
}

context(env: ToolEnv)
fun webTools() =
    if (env.webSearch) WebSearchTool.asTools() else emptyList()

context(env: ToolEnv)
fun extraTools() =
    env.toolSetBuilder?.invoke(env.chatToolSet)
        ?.flatMap { it.asTools() }
        ?: emptyList()

context(env: ToolEnv)
fun ruleTools() =
    env.ruleToolSet?.asTools() ?: emptyList()

context(env: ToolEnv)
fun buildToolRegistry(): ToolRegistry =
    ToolRegistry {
        tools(baseTools())
        tools(ruleTools())
        tools(webTools())
        tools(extraTools())
    }

fun buildChatToolSet(event: ProactiveSpeakEvent, context: Context): ChatToolSet {
    val currentBot = BotManage.getBot(event.botId)
    val groupId = event.groupId
    val bot = currentBot.refBot

    return QQChatToolSet(
        bot = bot,
        groupId = groupId.toLong(),
        context = context
    )
}

fun buildRuleToolSet(event: ProactiveSpeakEvent, context: Context): RuleToolSet? {
    return event.senderId?.let { senderId ->
        RuleToolSet(
            botId = event.botId,
            groupId = event.groupId,
            userId = senderId,
            admins = context.admins()
        )
    }
}

fun buildToolEnv(event: ProactiveSpeakEvent, context: Context): ToolEnv {
    return ToolEnv(
        buildChatToolSet(event, context),
        event.webSearch,
        event.toolSetBuilder,
        buildRuleToolSet(event, context)
    )
}