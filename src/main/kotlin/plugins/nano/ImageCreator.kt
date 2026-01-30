package uesugi.plugins.nano

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.contact.Group
import okio.Path.Companion.toPath
import okio.buffer
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.mapdb.Serializer
import plugins.Plugin
import plugins.SendAgentConf
import plugins.SendAgentState
import plugins.sendAgent
import uesugi.BotManage
import uesugi.core.LLMRouteRule
import uesugi.core.ProactiveSpeakFeature
import uesugi.core.RouteCallEvent
import uesugi.core.history.HistoryEntity
import uesugi.core.history.HistoryTable
import uesugi.core.history.MessageType
import uesugi.core.history.toRecord
import uesugi.toolkit.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO

class ImageCreator : Plugin {

    val imageClient = ImageClient()

    companion object {
        val log = logger()

        val AT_REGEX = Regex("@\\d+")
    }

    val scope =
        CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("ImageCreator") + CoroutineExceptionHandler { _, exception ->
            log.error("Image Creator error: {}", exception.message, exception)
        })

    val urlMapCache = MapDB.Cache.hashMap("image_creator_cache")
        .keySerializer(Serializer.STRING)
        .valueSerializer(Serializer.STRING)
        .expireAfterCreate(4, TimeUnit.HOURS)
        .createOrOpen()

    private lateinit var job: Job

    override fun onLoad() {
        val storage by ref<Storage>()
        job = EventBus.subscribeAsync<RouteCallEvent>(scope) { event ->
            if (event hit LLMRouteRule.IMAGE_CREATE) {
                val records = withContext(Dispatchers.IO) {
                    transaction {
                        HistoryEntity.find {
                            HistoryTable.groupId eq event.groupId and
                                    (HistoryTable.userId inList listOf(event.atFromId, event.botId))
                        }.orderBy(HistoryTable.createdAt to SortOrder.DESC)
                            .limit(10)
                            .with(HistoryEntity::resource)
                            .reversed()
                            .map {
                                it.toRecord()
                            }
                    }
                }

                val contentParts = records.onEach {
                    it.content = it.content?.replace(AT_REGEX, "")
                }
                    .mapNotNull { record ->
                        val role = when (record.userId) {
                            event.atFromId -> ContentPart.Role.ME
                            event.botId -> ContentPart.Role.AI
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
                                    val url = urlMapCache[resource.url]
                                    if (url != null) {
                                        ContentPart(url, ContentPart.Type.IMAGE, role)
                                    } else {
                                        val source = storage.get(resource.url.toPath())
                                        source.use {
                                            source.buffer()
                                                .inputStream()
                                                .use {
                                                    val uploadedFile = imageClient.upload(it, resource.fileName)
                                                    urlMapCache[resource.url] = uploadedFile.uri
                                                    ContentPart(uploadedFile.uri, ContentPart.Type.IMAGE, role)
                                                }
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

                val roledBot = BotManage.getBot(event.botId)
                val group = roledBot.refBot.getGroup(event.groupId.toLong())!!

                val state = atomic(false)

                sendAgent(
                    event.botId,
                    event.groupId,
                    "用户需要生成一张图片，请调用图片生成 Tool 生成图片。",
                    SendAgentConf(
                        toolSets = { toolSet ->
                            object : ToolSet {
                                @LLMDescription("回复消息，并生成图片发送，返回群其他人的回复")
                                @Tool
                                fun sendMessageAndImage(@LLMDescription("回复 2～3 句为主，最多 5 句") sentences: List<String>): String? {
                                    state.value = true
                                    scope.launch {
                                        val (msg, image) = deferred.await()
                                        toolSet.send(sentences).join()
                                        sendImage(msg, group, image)
                                    }
                                    return null
                                }
                            }
                        },
                        flag = ProactiveSpeakFeature.GRAB or ProactiveSpeakFeature.FALLBACK
                    ),
                    object : SendAgentState {
                        override val scope: CoroutineScope
                            get() = this@ImageCreator.scope

                        override fun sendAfter(sentences: List<String>) {
                            send()
                        }

                        override fun callCompletion() {
                            send()
                        }

                        override fun dispatchFallback() {
                            callCompletion()
                        }

                        private fun send() {
                            if (!state.value) {
                                state.value = true
                                scope.launch {
                                    val (msg, image) = deferred.await()
                                    sendImage(msg, group, image)
                                }
                            }
                        }
                    }
                )
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
        if (::job.isInitialized) {
            EventBus.unsubscribeAsync(job)
        }
        scope.cancel()
    }
}