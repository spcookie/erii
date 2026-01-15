package uesugi.plugins.chatgpt

import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.streaming.filterTextOnly
import kotlinx.coroutines.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.context.GlobalContext
import plugins.Plugin
import uesugi.BotManage
import uesugi.core.RouteCallEvent
import uesugi.core.RouteRule
import uesugi.core.history.HistoryService
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import kotlin.time.Duration.Companion.days

class ChatQA : Plugin {

    private val logger = logger()

    private val scope = CoroutineScope(
        SupervisorJob()
                + Dispatchers.Default
                + CoroutineName("ChatQA")
                + CoroutineExceptionHandler { _, exception ->
            logger.error("ChatQA Coroutine exception", exception)
        })

    private lateinit var job: Job

    override fun onLoad() {
        val historyService by GlobalContext.get().inject<HistoryService>()
        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()

        job = EventBus.subscribeAsync<RouteCallEvent>(scope) { event ->
            if (event.hit == RouteRule.DIRECT_QA) {

                val ctx = withContext(Dispatchers.IO) {
                    transaction {
                        val history = historyService.getLatestHistory(event.botId, event.groupId, 10, 1.days)
                        buildString {
                            for (entity in history) {
                                appendLine("${entity.nick}: ${entity.content}")
                            }
                        }
                    }
                }

                val prompt = prompt("QA") {
                    system(
                        """
                        你是一个专业的问答助手，只回答用户提出的问题的核心内容，保持简洁明了。  

                        要求：
                        1. 不要输出 Markdown 或列表符号。
                        2. 直接输出文本答案，不要多余前缀、标题或注释。
                        3. 如果问题可以简短回答就直接回答，不要冗长。
                        4. 对于复杂问题，也尽量保持回答直截了当、可理解。
                        5. 回答的句子末尾需要换行。
                    """.trimIndent()
                    )

                    user(
                        """
                        当前上下文：
                        $ctx
                            
                        用户问题：
                        ${event.input}

                        请给出直接的答案：
                    """.trimIndent()
                    )
                }

                val bot = BotManage.getBot(event.botId)!!.bot
                val group = bot.getGroup(event.groupId.toLong())!!

                val buffer = StringBuilder()
                promptExecutor.executeStreaming(
                    prompt,
                    GoogleModels.Gemini2_5Pro
                ).filterTextOnly()
                    .collect { chunk ->
                        buffer.append(chunk)
                        val parts = buffer.toString()
                            .lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .toList()
                        parts.dropLast(1).forEach { msg ->
                            msg.trim().takeIf { it.isNotEmpty() }?.let { group.sendMessage(it) }
                        }
                        buffer.clear()
                        buffer.append(parts.last())
                    }
                buffer.toString().trim().takeIf { it.isNotEmpty() }?.let { group.sendMessage(it) }
            }
        }
    }

    override fun onUnload() {
        if (::job.isInitialized) {
            EventBus.unsubscribeAsync(job)
        }
    }

}