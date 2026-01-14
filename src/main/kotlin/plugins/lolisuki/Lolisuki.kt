package uesugi.plugins.lolisuki

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.structure.executeStructured
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.context.GlobalContext
import plugins.Plugin
import plugins.SendAgentState
import plugins.sendAgent
import uesugi.BotManage
import uesugi.core.ChatToolSet
import uesugi.core.RouteCallEvent
import uesugi.core.RouteRule
import uesugi.core.history.HistoryService
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import java.net.URL
import kotlin.time.Duration.Companion.days

class Lolisuki : Plugin {

    val client by GlobalContext.get().inject<HttpClient>()

    private val log = logger()

    internal val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var job: Job

    override fun onLoad() {
        val historyService by GlobalContext.get().inject<HistoryService>()
        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()

        job = EventBus.subscribeAsync<RouteCallEvent>(scope) { event ->
            if (event.hit == RouteRule.REQUEST_R18_CONTENT) {

                @Serializable
                @LLMDescription("标签")
                data class Tag(val values: List<String>)

                @Serializable
                @LLMDescription("标签组")
                data class TagGroup(val groups: List<Tag>)

                val ctx = withContext(Dispatchers.IO) {
                    transaction {
                        val history = historyService.getLatestHistory(event.botId, event.groupId, 10, 1.days)
                        buildString {
                            for (entity in history) {
                                appendLine("${entity.userId}: ${entity.content}")
                            }
                        }
                    }
                }

                val prompt = prompt("提取标题、关键词") {
                    user(
                        """
                            请根据以下规则提取下面内容中索要图片的关键词、标签(Tag)
                            
                            规则：
                                - 历史上下文提供给你参考，连接用户意图
                                - 只提取“当前内容”中的关键词、标签
                                - 可以提取多个关键词
                                - 多个关键词如果是 AND 语义，则是一个“标签组”，OR 语义，则是多个“标签组”
                                - 如果没有关键词、标签可以提取，不要捏造，返回空
                                
                            历史上下文：
                            $ctx
                                
                            当前内容：
                            ${event.input}
                        """.trimIndent()
                    )
                }

                log.info("用户输入：${event.input}，开始提取关键词")

                val result = promptExecutor.executeStructured<TagGroup>(
                    prompt,
                    GoogleModels.Gemini2_5FlashLite
                )

                var tags: List<String>? = null
                if (result.isSuccess) {
                    tags = result.getOrNull()?.data?.run {
                        buildList {
                            for (group in groups) {
                                add(group.values.joinToString("|"))
                            }
                        }
                    }
                }

                if (tags.isNullOrEmpty()) {
                    log.info("未提取到关键词、标签")
                } else {
                    log.info("提取到关键词、标签: ${tags.joinToString("&")}")
                }

                val node: JsonNode = client.get("https://lolisuki.cn/api/setu/v1") {
                    parameter("r18", 1)
                    parameter("level", 4)
                    parameter("num", 4)
                    if (tags != null) {
                        for (tag in tags) {
                            parameter("tag", tag)
                        }
                    }
                    log.info("开始获取图片连接: $url")
                }.body()
                if (node.get("code").asInt() != 0) {
                    log.error("Lolisuki error: ${node.get("message").asText()}")
                } else {
                    val roledBot = BotManage.getBot(event.botId)!!
                    val bot = roledBot.bot
                    val group = bot.getGroup(event.groupId.toLong())!!

                    var image: Image? = null
                    for (i in 0 until 4) {
                        val url = node.get("data")[i].get("urls").get("regular").asText()
                        log.info("开始获取图片: $url")
                        try {
                            val connection = URL(url).openConnection()
                                .apply {
                                    connectTimeout = 20_000
                                    readTimeout = 60_000
                                }
                            image = connection.getInputStream().use { input ->
                                input.toExternalResource().use {
                                    group.uploadImage(it)
                                }
                            }
                            break
                        } catch (_: Exception) {
                            log.info("图片: $url 获取失败，开始获取下一张图片")
                        }
                    }
                    if (image == null) {
                        log.warn("未获取到图片")
                    }
                    sendAgent(
                        botId = event.botId,
                        groupId = event.groupId,
                        input = "加入群聊天，如果群友向你索要涩图，你需要调用工具发送一张涩图给群友。",
                        toolSets = { ImageTool(image, group, it) },
                        chatPointRule = "如果群友索要涩图，请发送。",
                        state = object : SendAgentState {}
                    )
                }
            }
        }
    }

    override fun onUnload() {
        if (::job.isInitialized) {
            EventBus.unsubscribeAsync(job)
        }
    }

    class ImageTool(val image: Image?, val group: Group, val chatToolSet: ChatToolSet) : ToolSet {

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        @LLMDescription("回复消息，并发送涩图，返回群其他人的回复")
        @Tool
        fun sendMessageAndImage(@LLMDescription("回复 2～3 句为主，最多 5 句") sentences: List<String>): String? {
            scope.launch {
                val job = chatToolSet.send(sentences)
                if (image != null) {
                    job.join()
                    val message = MessageChainBuilder().append(image).build()
                    group.sendMessage(message)
                }
            }
            return null
        }

    }
}