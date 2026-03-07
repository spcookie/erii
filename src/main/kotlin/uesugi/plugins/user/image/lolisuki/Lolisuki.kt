package uesugi.plugins.user.image.lolisuki

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.structure.executeStructured
import com.fasterxml.jackson.databind.JsonNode
import com.google.auto.service.AutoService
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.contact.Group
import net.mamoe.mirai.utils.ExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import uesugi.config.LLMModelsChoice
import uesugi.core.ChatToolSet
import uesugi.core.ProactiveSpeakFeature
import uesugi.core.plugin.*
import uesugi.core.plugin.MetaToolSet.Companion.meta
import uesugi.plugins.getGroup
import uesugi.plugins.getLatestHistory
import uesugi.toolkit.logger
import java.net.URL
import kotlin.time.Duration.Companion.days

@AutoService(Plugin::class)
class Lolisuki : RoutePlugin, ClassNameMixin {

    private val log = logger()

    val scope =
        CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("Lolisuki") + CoroutineExceptionHandler { _, exception ->
            log.error("Lolisuki error: {}", exception.message, exception)
        })

    override fun onLoad(context: PluginContext) {
        context.chain { meta ->
            val image = getImage(meta)

            val state = atomic(false)

            val group = meta.getGroup()

            fun send() {
                if (!state.value) {
                    state.value = true
                    if (image != null) {
                        log.info("由于图片未使用 Agent Tool 发送，尝试直接发送")
                        meta.roledBot.refBot.launch {
                            image.use {
                                group.sendImage(image)
                            }
                            log.info("图片直接发送成功")
                        }
                    } else {
                        log.warn("未获取到图片，直接发送失败")
                    }
                }
            }

            meta.sendAgent(
                input = "加入群聊天，你已经获取到了一张涩图，你需要调用工具发送一张涩图给群友。",
                SendAgentConf(
                    toolSets = { listOf(ImageTool(image, group, it, state)) },
                    flag = ProactiveSpeakFeature.GRAB or ProactiveSpeakFeature.FALLBACK
                )
            ) {
                sendBefore { send() }

                callCompletion { send() }

                dispatchFallback { send() }

                this@Lolisuki.scope
            }
        }

        context.tool {
            {
                object : MetaToolSet {
                    @Tool
                    @LLMDescription("回复消息，并发送一张涩图")
                    suspend fun sendSexImage(@LLMDescription("回复 2～3 句") sentences: List<String>): String {
                        val resource = getImage(meta)
                        val group = meta.getGroup()
                        resource?.use {
                            for (sentence in sentences) {
                                group.sendMessage(sentence)
                            }
                            group.sendImage(resource)
                        }
                        return "ok"
                    }
                }
            }
        }
    }

    suspend fun PluginContext.getImage(meta: Meta): ExternalResource? {
        val history = database.getLatestHistory(
            meta.botId,
            meta.groupId,
            10,
            1.days
        )
        val ctx = buildString {
            for (entity in history) {
                appendLine("${entity.nick}: ${entity.content}")
            }
        }

        val prompt = prompt("提取标题、关键词") {
            user(
                """
                        请根据以下规则提取“当前内容”中索要的二次元/动漫图片的关键词或标签(Tag)，仅限二次元、动漫、游戏角色或风格类标签。
                
                        规则：
                        1. 仅提取 **二次元图片相关标签**，如角色名、画风、类型（萌系、赛博朋克、机甲等）。
                        2. 忽略泛泛的词语，如“涩图”“图片”“发一下”等，不要捏造。
                        3. 可以提取多个关键词。
                        4. 多个关键词如果是 **AND** 语义，则是一个标签组；如果是 **OR** 语义，则是多个标签组。
                        5. 如果没有可提取的标签，返回空列表。
                        6. 历史上下文仅作为参考，帮助理解用户意图。
                
                        历史上下文：
                        $ctx
                
                        当前内容：
                        ${meta.input}
                        """.trimIndent()
            )
        }

        log.info("用户输入：${meta.input}，开始提取关键词")

        val result = llm.executeStructured<TagGroup>(
            prompt,
            LLMModelsChoice.Lite
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

        val node: JsonNode = http.get("https://lolisuki.cn/api/setu/v1") {
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
        var image: ExternalResource? = null
        if (node.get("code").asInt() != 0) {
            log.error("获取图片连接失败: $node")
        } else {
            for (i in 0 until 4) {
                var url: String? = null
                try {
                    url = node.get("data")[i].get("urls").get("regular").asText()
                    log.info("开始获取图片: $url")
                    withContext(Dispatchers.IO) {
                        val connection = URL(url).openConnection()
                            .apply {
                                connectTimeout = 20_000
                                readTimeout = 60_000
                            }
                        image = connection.getInputStream().use { input ->
                            input.toExternalResource()
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
        }
        return image
    }

    override val matcher: Pair<String, String>
        get() = "REQUEST_R18_IMAGE" to """
        仅当用户【明确提出图片请求】时，才选择此分类。
        必须【同时满足】以下两个条件，缺一不可：

        条件一：图片内容明确指向【成人 / R18】
        例如：
        - “涩图”
        - “R18”
        - “黄图”
        - “色图”

        强制排除规则：
        - 如果内容仅包含成人、性、下流、攻击性、辱骂、玩梗等文字
        - 但【没有任何索要图片的意图或行为】
        则【绝对不要】归类为 REQUEST_R18_IMAGE。
        这种情况应优先归类为 CHAT。
        """.trimIndent()

    @Serializable
    @LLMDescription("标签")
    data class Tag(val values: List<String>)

    @Serializable
    @LLMDescription("标签组")
    data class TagGroup(val groups: List<Tag>)

    @Suppress("unused")
    inner class ImageTool(
        val image: ExternalResource?,
        val group: Group,
        val chatToolSet: ChatToolSet,
        val state: AtomicBoolean
    ) : ToolSet {

        @LLMDescription("回复消息，并发送涩图，返回群其他人的回复")
        @Tool
        fun sendMessageAndImage(@LLMDescription("回复 2～3 句为主，最多 5 句") sentences: List<String>): String? {
            state.value = true
            scope.launch {
                val job = chatToolSet.send(sentences)
                if (image != null) {
                    job.join()
                    image.use {
                        group.sendImage(image)
                    }
                }
            }
            return null
        }

    }
}