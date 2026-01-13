package uesugi.plugins.lolisuki

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import com.fasterxml.jackson.databind.JsonNode
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.koin.core.context.GlobalContext
import plugins.Plugin
import plugins.SendAgentState
import plugins.sendAgent
import uesugi.BotManage
import uesugi.core.ChatToolSet
import uesugi.core.RouteCallEvent
import uesugi.core.RouteRule
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import java.net.URL

class Lolisuki : Plugin {

    val client by GlobalContext.get().inject<HttpClient>()

    private val log = logger()

    internal val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var job: Job

    override fun onLoad() {
//        val promptExecutor by GlobalContext.get().inject<PromptExecutor>()
        job = EventBus.subscribeAsync<RouteCallEvent>(scope) { event ->
            if (event.hit == RouteRule.REQUEST_R18_CONTENT) {
                val node: JsonNode = client.get("https://lolisuki.cn/api/setu/v1") {
                    parameter("r18", 1)
                    parameter("level", 4)
                    parameter("num", 4)
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
                            // ignore
                        }
                    }
                    if (image == null) throw RuntimeException("Lolisuki error: 无法获取图片")
                    sendAgent(
                        botId = event.botId,
                        groupId = event.groupId,
                        input = "加入群聊天，如果群友向你索要涩图，你需要发送一张涩图给群友。",
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

    class ImageTool(val image: Image, val group: Group, val chatToolSet: ChatToolSet) : ToolSet {

        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        @LLMDescription("回复消息，并发送涩图，返回群其他人的回复")
        @Tool
        fun sendMessageAndImage(@LLMDescription("回复 2～3 句为主，最多 5 句") sentences: List<String>): String? {
            scope.launch {
                val job = chatToolSet.send(sentences)
                job.join()
                val message = MessageChainBuilder().append(image).build()
                group.sendMessage(message)
            }
            return null
        }

    }
}