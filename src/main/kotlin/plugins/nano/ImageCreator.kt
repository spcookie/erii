package uesugi.plugins.nano

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.contact.Group
import plugins.Plugin
import plugins.SendAgentConf
import plugins.SendAgentState
import plugins.sendAgent
import uesugi.BotManage
import uesugi.core.ProactiveSpeakFeature
import uesugi.core.RouteCallEvent
import uesugi.core.RouteRule
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class ImageCreator : Plugin {

    val imageClient = ImageClient()

    val log = logger()

    val scope =
        CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("ImageCreator") + CoroutineExceptionHandler { _, exception ->
            log.error("Image Creator error: {}", exception.message, exception)
        })

    private lateinit var job: Job

    override fun onLoad() {
        job = EventBus.subscribeAsync<RouteCallEvent>(scope) { event ->
            if (event hit RouteRule.IMAGE_CREATE) {
                val deferred = scope.async(Dispatchers.IO) {
                    imageClient.generate(
                        event.input,
                        null,
                        null,
                        null,
                        1f,
                        32768,
                        0.98f,
                        "1K",
                        "AUTO",
                        "PRO"
                    ).first
                }

                val roledBot = BotManage.getBot(event.botId)!!
                val group = roledBot.bot.getGroup(event.groupId.toLong())!!

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