package uesugi.plugins.user.image.nano

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.contact.Group
import okio.Path.Companion.toPath
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import uesugi.core.ProactiveSpeakFeature
import uesugi.core.message.history.HistoryTable
import uesugi.core.message.history.MessageType
import uesugi.core.message.resource.ResourceTable
import uesugi.core.plugin.*
import uesugi.plugins.getGroup
import uesugi.toolkit.logger
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

//@AutoService(Plugin::class)
class ImageCreator : RoutePlugin, ClassNameMixin {

    val imageClient = ImageClient()

    companion object {
        val log = logger()

        val AT_REGEX = Regex("@\\d+")
    }

    val scope =
        CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("ImageCreator") + CoroutineExceptionHandler { _, exception ->
            log.error("Image Creator error: {}", exception.message, exception)
        })

    override fun onLoad(context: PluginContext) {
        context.chain { meta ->
            val records = database.getHistory {
                (HistoryTable innerJoin ResourceTable).selectAll()
                    .where {
                        HistoryTable.groupId eq meta.groupId and
                                (HistoryTable.userId inList listOf(meta.senderId!!, meta.botId))
                    }
                    .orderBy(HistoryTable.createdAt to SortOrder.DESC)
                    .limit(10)
            }.reversed()

            val contentParts = records.onEach {
                it.content = it.content?.replace(AT_REGEX, "")
            }.mapNotNull { record ->
                val role = when (record.userId) {
                    meta.senderId -> ContentPart.Role.ME
                    meta.botId -> ContentPart.Role.AI
                    else -> return@mapNotNull null
                }
                when (record.messageType) {
                    MessageType.TEXT -> {
                        ContentPart(record.content!!, ContentPart.Type.TEXT, role)
                    }

                    MessageType.IMAGE -> {
                        val resource = record.resource
                        if (resource == null) {
                            ContentPart(record.content!!, ContentPart.Type.TEXT, role)
                        } else {
                            val url = mem.get(resource.url)
                            if (url != null) {
                                ContentPart(url, ContentPart.Type.IMAGE, role)
                            } else {
                                val source = blob.get(resource.url.toPath())
                                source.use {
                                    val uploadedFile = imageClient.upload(it, resource.fileName)
                                    mem.set(resource.url, uploadedFile.uri)
                                    ContentPart(uploadedFile.uri, ContentPart.Type.IMAGE, role)
                                }
                            }
                        }
                    }

                    else -> null
                }
            }.toList()

            val deferred = scope.async(Dispatchers.IO) {
                imageClient.generate(
                    contentParts,
                    null,
                    null,
                    1f,
                    32768,
                    0.98f,
                    "1K",
                    "PRO"
                ).first
            }

            val group = meta.getGroup()

            val state = atomic(false)

            fun send() {
                if (!state.value) {
                    state.value = true
                    scope.launch {
                        val (msg, image) = deferred.await()
                        sendImage(msg, group, image)
                    }
                }
            }

            meta.sendAgent(
                "用户需要生成一张图片，请调用图片生成 Tool 生成图片。",
                SendAgentConf(
                    toolSets = { toolSet ->
                        listOf(
                            object : ToolSet {
                                @LLMDescription("回复消息，并生成图片发送，返回群其他人的回复")
                                @Tool
                                fun sendMessageAndImage(@LLMDescription("回复 2～3 句为主，最多 5 句") sentences: List<String>): String? {
                                    state.value = true
                                    scope.launch {
                                        toolSet.send(sentences).join()
                                        val (msg, image) = deferred.await()
                                        sendImage(msg, group, image)
                                    }
                                    return null
                                }
                            }
                        )
                    },
                    flag = ProactiveSpeakFeature.GRAB or ProactiveSpeakFeature.FALLBACK
                )
            ) {
                sendAfter { send() }
                callCompletion { send() }
                dispatchFallback { send() }
                this@ImageCreator.scope
            }

        }

    }

    private suspend fun sendImage(
        msg: String?,
        group: Group,
        image: BufferedImage?
    ) {
        if (msg != null) {
            group.sendMessage(msg)
        }
        if (image != null) {
            ByteArrayOutputStream().use { out ->
                ImageIO.write(image, "png", out)
                ByteArrayInputStream(out.toByteArray()).use { input ->
                    group.sendImage(input)
                }
            }
        } else {
            log.warn("未生成图片")
        }
    }

    override fun onUnload() {
        scope.cancel()
    }

    override val matcher: Pair<String, String>
        get() = "IMAGE_CREATE" to """
        当用户的核心意图是【让 AI 生产/制作/定制一张图片】时，优先归类为此项。
        
        【优先级规则】：
        如果用户的意图同时涉及“涩图/R18”和“生成/画”，
        必须优先归类为 IMAGE_CREATE（视为“创作R18图片”），而不是 REQUEST_R18_IMAGE。

        判定逻辑 - 满足以下任一情况即可：

        1. 显式创作指令（动词驱动）：
           - 包含明确的生产类动词：“画”、“生成”、“捏”、“绘制”、“制作”、“咏唱”。
           - 例：“画一只猫”、“生成赛博朋克背景”、“帮我捏个头像”。

        2. 隐式定制意图（描述驱动）：
           - 用户使用了“想要一张图”、“整一张图”等模糊动词，但**紧跟了具体的画面描述**。
           - 这里的“描述”通常包含：外貌特征、动作、场景、风格、构图。
           - 例：“给我一张[白发、红瞳、拿着武士刀]的图片”（因为描述具体，视为要求生成）。
           - 例：“想要一张[初音未来在吃汉堡]的图”（场景具体，视为要求生成）。

        边界区分（Make vs Get）：
        - 场景 A（生成）：用户提供了画面配方/Prompt。
          “整一张黑丝御姐的图” -> IMAGE_CREATE（偏向定制需求）。
          “画个涩图” -> IMAGE_CREATE（动词是画）。
        
        - 场景 B（索取）：用户仅仅是在翻找库存。
          “发点黑丝御姐” -> REQUEST_R18_IMAGE / CHAT（意图是看现成的）。
          “来点涩图” -> REQUEST_R18_IMAGE（意图是看现成的）。
          “有没有美图” -> CHAT（询问库存）。

        总结：只要感觉用户是把 AI 当作【画师/生成器】使用，就选此项。
        """.trimIndent()

}