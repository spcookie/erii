package uesugi.core

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.markdown.markdown
import ai.koog.prompt.structure.executeStructured
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koin.core.context.GlobalContext
import uesugi.core.history.HistoryService
import uesugi.core.history.toRecord
import uesugi.core.memory.MemoryService
import uesugi.toolkit.logger
import kotlin.time.Duration.Companion.hours


object RoutingAgent {

    private val log = logger()

    suspend fun route(botId: String, groupId: String, message: String): RouteRule {
        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()
        val historyService by GlobalContext.get().inject<HistoryService>()
        val memoryService by GlobalContext.get().inject<MemoryService>()

        val summaryEntity = memoryService.getSummary(botId, groupId)
        val latestHistory = historyService.getLatestHistory(botId, groupId, 50, 24.hours).map { it.toRecord() }

        val prompt = prompt("RoutingAgent") {
            system {
                markdown {
                    h1("你是一个【消息路由判定器】，负责根据用户的输入内容，判断应当使用哪一条处理规则。")

                    text("【可用规则枚举】")
                    bulleted {
                        for (rule in RouteRule.entries) {
                            item { text("${rule.name}: ${rule.title}") }
                        }
                    }

                    text("【判定原则】")
                    numbered {
                        item { text("只关注【当前用户消息】的真实意图") }
                        item { text("群聊上下文仅用于消歧，不可过度联想") }
                        item { text("无法确定时，默认选择 CHAT_URGENT（宁可保守）") }
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
            val result = promptExecutor.executeStructured<RouteRuleRef>(
                prompt,
                model = GoogleModels.Gemini2_5FlashLite
            )

            return result.getOrThrow().data.ref
        } catch (e: Exception) {
            log.warn("routing failed, dispatchFallback CHAT_URGENT, reason: {}", e.message)
            return RouteRule.CHAT
        }
    }
}

@SerialName("RouteRule")
@Serializable
data class RouteRuleRef(
    val ref: RouteRule
)