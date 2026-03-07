package uesugi.plugins.user.image.seeddream

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.dsl.prompt
import ai.koog.prompt.structure.executeStructured
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.fasterxml.jackson.databind.JsonNode
import com.google.auto.service.AutoService
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.utils.ExternalResource
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import uesugi.config.LLMModelsChoice
import uesugi.core.ProactiveSpeakFeature
import uesugi.core.message.history.HistoryTable
import uesugi.core.message.history.MessageType
import uesugi.core.message.resource.ResourceTable
import uesugi.core.plugin.*
import uesugi.core.plugin.MetaToolSet.Companion.meta
import uesugi.plugins.getGroup
import uesugi.toolkit.logger
import java.net.URL
import kotlin.io.encoding.Base64

@AutoService(Plugin::class)
class SeedDream : RoutePlugin, ClassNameMixin {

    companion object {

        private val log = logger()

        private val IMAGE_TASK_AGGREGATOR_PROMPT = """
                你是一个“群聊图片生成任务聚合器”。
                
                你的职责是：
                你不负责生成图片。
                从提供的群聊记录中，提取当前针对图片生成或图片修改的最终有效指令，
                并转换为图像模型可直接调用的结构化参数。
                
                你将收到：

                1. 群聊记录（按时间升序排列）
                2. 机器人、用户历史图片列表（有id）
                
                # 一、你的目标
                
                你必须完成以下事情：
                
                1. 判断是否为图片生成任务
                2. 聚合最终有效提示词
                3. 判断任务类型
                4. 提取使用到的图片ID
                5. 输出严格JSON结构
                
                # 二、图片生成任务识别规则
                
                作用域收敛规则:
                
                - 只分析“最后一次涉及图片任务的对话块”。
                - 忽略闲聊和无关插话。
                - 若存在多条修改指令，以最后一个有效修改为准。
                - 若连续多轮修改同一张图片，必须整合全部有效修改。
                
                当满足以下任一条件时，判定为图片生成任务：
                
                - 出现 “生成一张”、“画一张”、“做一张”、“帮我生成”、“根据图片生成”
                - 出现 “改成”、“变成”、“去掉”、“替换”、“编辑”
                - 明确 @机器人 并伴随图像生成相关描述
                - 出现图片引用 [image id=x] 并伴随生成/修改描述
                - 出现“根据上述图片”、“根据这张图”、“参考图片”等指代
                
                否则判定为非图片任务。
                
                # 三、任务类型判断规则
                
                ## 1. 文生图 (text_to_image)
                
                满足：
                - 仅有文本描述
                - 未使用任何图片ID
                - 或文本内容不依赖图片
                
                ## 2. 图生图 - 参考图生成 (image_to_image_reference)
                
                满足：
                - 使用图片作为参考
                - 表述为“根据图片生成…”
                - 没有修改原图内容
                - 是基于图进行延展生成新图
                
                ## 3. 图生图 - 图片编辑 (image_edit)
                
                满足：
                - 明确要修改某张图片
                - 表述为“改成”、“把…换成”、“去掉背景”、“修改”
                - 对原图内容进行直接编辑
                
                # 四、图片ID提取规则
                
                必须严格遵守：
                
                1. 只提取用户发送的图片
                2. 机器人生成的图片不得作为输入图片
                3. 若用户说“上述图片”、“根据图片”，指代最近一张用户图片
                4. 若用户重复指令但未新增图片，不重复添加
                5. 若用户新增图片，使用最新图片
                6. 只使用任务发起前出现的图片
                
                # 五、多轮聚合规则
                
                1. 若文本一致但图片不同 → 使用最新图片
                2. 若文本补充说明 → 合并为最终提示词
                3. 若无新增信息 → 忽略重复
                
                # 六、提示词处理规则
                
                1. 保留用户原始表达语义
                2. 解析指代词
                3. 不添加艺术风格
                4. 不扩展细节
                5. 不润色
                6. 仅做语义消歧与完整化
                
                # 七、输出格式（必须严格输出JSON）
                
                你必须只输出以下结构，不得输出额外解释：
                
                {
                  "taskType": "TEXT_TO_IMAGE | IMAGE_TO_IMAGE_REFERENCE | IMAGE_EDIT",
                  "prompt": "最终用于生成图片的完整提示词",
                  "imageIds": ["ID1","ID2"],
                  "confidence": 0.0
                }
                
                字段说明：
                
                - taskType：三选一
                - prompt：最终聚合后的提示词
                - imageIds：字符串数组
                - confidence：0~1之间的小数
                
                如果不是图片任务，输出：
                
                {
                  "taskType": null,
                  "prompt": null,
                  "imageIds": [],
                  "confidence": 0.0
                }
                
                严格执行规则。
                不得输出除JSON以外的任何内容。
            """.trimIndent()
    }

    @Serializable
    @LLMDescription("群聊图片生成任务聚合引擎的输入参数")
    data class SeedDreamRequest(
        @property:LLMDescription("图片生成任务类型")
        var taskType: SeedDreamType,
        @property:LLMDescription("最终完整prompt")
        var prompt: String,
        @property:LLMDescription("被修改图片的id列表")
        var imageIds: List<Int>? = null,
        @property:LLMDescription("置信度，0~1之间的小数")
        var confidence: Float = 0.0f
    )

    @Suppress("unused")
    enum class SeedDreamType {
        TEXT_TO_IMAGE,
        IMAGE_TO_IMAGE_REFERENCE,
        IMAGE_EDIT
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun onLoad(context: PluginContext) {
        suspend fun image(meta: Meta): Either<String, ExternalResource> {
            with(context) {
                val records = database.getHistory {
                    (HistoryTable leftJoin ResourceTable).selectAll()
                        .where {
                            HistoryTable.groupId eq meta.groupId and
                                    (HistoryTable.userId inList listOf(meta.senderId!!, meta.botId))
                        }
                        .orderBy(HistoryTable.createdAt to SortOrder.DESC)
                        .limit(14)
                }.reversed()

                val prompt = records.mapIndexedNotNull { index, record ->
                    val role = if (record.userId == meta.senderId) "User: " else "AI: "
                    when (record.messageType) {
                        MessageType.TEXT -> {
                            "$role${record.content}"
                        }

                        MessageType.IMAGE -> {
                            "$role[image id=${index}]"
                        }

                        else -> {
                            null
                        }
                    }
                }.joinToString("\n\n")

                val promt = prompt("seed_dream_prompt") {
                    system(IMAGE_TASK_AGGREGATOR_PROMPT)
                    user(prompt)
                }

                val result = llm.executeStructured<SeedDreamRequest>(promt, LLMModelsChoice.Pro)

                val request = result.getOrThrow().data

                log.debug("SeedDream Request: {}", request)

                val images = if (request.taskType != SeedDreamType.TEXT_TO_IMAGE) {
                    records.filterIndexed { index, record ->
                        record.messageType == MessageType.IMAGE && request.imageIds?.contains(index) ?: false
                    }
                        .mapNotNull {
                            it.resource?.let { resource ->
                                resource.bytes ?: return@mapNotNull null
                                Pair(resource.bytes, resource.fileName)
                            } ?: return@mapNotNull null
                        }
                        .map { (bytes, fileName) ->
                            val base64 = Base64.encode(bytes!!)
                            val mimeType = getMimeType(fileName)
                            "data:$mimeType;base64,$base64"
                        }
                } else {
                    listOf()
                }

                val node: JsonNode = http.post("https://ark.cn-beijing.volces.com/api/v3/images/generations") {
                    contentType(ContentType.Application.Json)
                    bearerAuth("d89e5cf3-29af-4efc-9ffd-23064fd0838d")
                    setBody(
                        mapOf(
                            "model" to "doubao-seedream-4-5-251128",
                            "prompt" to request.prompt,
                            "image" to if (images.isEmpty()) null else if (images.size == 1) images[0] else images,
                            "response_format" to "url",
                            "size" to "2K",
                            "stream" to false,
                            "watermark" to false
                        )
                    )
                }.body()

                if (node.has("error")) {
                    log.warn("SeedDream Error: {}", node.toString())
                    return node.toString().left()
                }

                val url: String? = node.path("data")
                    .path(0)
                    .path("url")
                    .textValue()

                if (url == null) {
                    return "No URL returned by SeedDream".left()
                }
                val connection = URL(url).openConnection()
                    .apply {
                        connectTimeout = 20_000
                        readTimeout = 60_000
                    }
                return connection.getInputStream()
                    .use { input ->
                        input.toExternalResource().right()
                    }
            }
        }

        context.chain { meta ->
            val resource = coroutineScope {
                async {
                    image(meta)
                }
            }.await()

            val group = meta.getGroup()

            val state = atomic(false)

            fun send() {
                if (!state.value) {
                    state.value = true
                    GlobalScope.launch {
                        resource.onRight { img ->
                            log.info("由于图片未使用 Agent Tool 发送，尝试直接发送")
                            img.use {
                                group.sendImage(it)
                            }
                        }.onLeft { log.warn("未获取到图片，直接发送失败") }
                    }
                }
            }

            meta.sendAgent(
                input = resource.fold(
                    { error -> "生成图片失败，发送消息告诉用户生成失败，错误: $error" },
                    { _ -> "你已经生成了一张图片，必须调用图片 Tool 发送生成图片。" }
                ),
                SendAgentConf(
                    toolSets = {

                        listOf(
                            object : ToolSet {
                                @LLMDescription("回复消息，并发送图片，返回群其他人的回复")
                                @Tool
                                fun sendMessageAndImage(@LLMDescription("回复 2～3 句为主，最多 5 句") sentences: List<String>): String? {
                                    state.value = true
                                    GlobalScope.launch {
                                        val job = it.send(sentences)
                                        resource.onRight { img ->
                                            job.join()
                                            img.use {
                                                group.sendImage(it)
                                            }
                                        }.onLeft { log.warn("未获取到图片，发送失败") }
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
                dispatchFallback { send() }
                callCompletion { send() }
                GlobalScope
            }
        }

        context.tool {
            {
                object : MetaToolSet {
                    @Tool
                    @LLMDescription("回复消息，并生成图片发送")
                    suspend fun imageCreate(@LLMDescription("回复 2～3 句") sentences: List<String>): String {
                        val resource = image(meta)
                        val group = meta.getGroup()
                        resource.onRight { img ->
                            img.use {
                                for (sentence in sentences) {
                                    group.sendMessage(sentence)
                                }
                                group.sendImage(it)
                            }
                        }
                        return "ok"
                    }
                }
            }
        }
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

    fun getMimeType(fileName: String): String {
        return when (val type = fileName.substringAfterLast(".")) {
            "png" -> "image/png"
            "jpg", "jpeg", "gif" -> "image/jpeg"
            else -> throw IllegalArgumentException("Unsupported image type: $type")
        }
    }
}