package uesugi.core.route

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.message.Message
import uesugi.common.LLMModelsChoice
import uesugi.common.logger
import uesugi.common.ref
import uesugi.core.agent.buildHistoriesPrompt
import uesugi.core.agent.buildSummaryPrompt
import uesugi.core.message.history.HistoryService
import uesugi.core.state.memory.MemoryService
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime


object RoutingAgent {

    private val log = logger()

    @OptIn(ExperimentalTime::class)
    suspend fun route(botId: String, groupId: String, message: String): LLMRouteRule {
        val promptExecutor by ref<PromptExecutor>()
        val historyService by ref<HistoryService>()
        val memoryService by ref<MemoryService>()

        val summaryEntity = memoryService.getSummary(botId, groupId)
        val latestHistory = historyService.getLatestHistory(botId, groupId, 50, 24.hours)

        val prompt = prompt("RoutingAgent") {
            system {
                markdown {
                    h1("你是一个【消息路由判定器】，负责根据用户的输入内容，判断应当使用哪一条处理规则。")

                    text("【可用规则枚举】")
                    table(
                        headers = listOf("规则名称", "规则描述"),
                        rows = buildList {
                            for (rule in RouteRuleRegister.getAllRules()) {
                                add(listOf(rule.name, rule.description))
                            }
                        }
                    )

                    text("【判定原则】")
                    numbered {
                        item { text("只关注【当前用户消息】的真实意图") }
                        item { text("群聊上下文仅用于消歧，不可过度联想") }
                        item { text("无法确定时，默认选择 CHAT（宁可保守）") }
                    }

                    text("【输出结果约束】")
                    numbered {
                        item { text("只输出“规则名称”") }
                        item { text("不要包含任何其他文字") }
                    }
                }
            }
            user {
                markdown {
                    text("【群聊消息摘要】")
                    buildSummaryPrompt(summaryEntity)

                    text("【最近的聊天记录】")
                    buildHistoriesPrompt(latestHistory, botId)

                    header(3, "当前用户消息")
                    text(message)
                }
            }
        }

        try {
            val result = promptExecutor.execute(
                prompt,
                model = LLMModelsChoice.Lite,
            )
            val assistant = result.filterIsInstance<Message.Assistant>().first()
            return RouteRuleRegister.getRule(assistant.content.trim())!!
        } catch (e: Exception) {
            log.warn("routing failed, dispatch fallback CHAT_URGENT, reason: {}", e.message)
            return RouteRuleRegister.getRule("CHAT")!!
        }
    }
}