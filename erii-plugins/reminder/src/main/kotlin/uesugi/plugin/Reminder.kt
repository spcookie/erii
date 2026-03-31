package uesugi.plugin

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.buildMessageChain
import org.pf4j.Extension
import uesugi.common.BotManage
import uesugi.common.PSFeature
import uesugi.spi.*

@PluginDefinition
class Reminder : AgentPlugin()

@Extension
class ReminderExtension : AgentExtension<Reminder> {

    private var scheduler: ReminderScheduler? = null

    override fun onLoad(context: PluginContext) {
        val store = ReminderStoreImpl(context.kv)
        val wheel = ReminderWheel(store)

        runBlocking { wheel.init() }

        scheduler = ReminderScheduler(context.scheduler, wheel) { task ->
            val targetMention = task.targetUserId?.let { "@$it " } ?: ""
            sendAgent(
                task.botId,
                task.groupId,
                """
                    提醒时间到了，你需要在群里发送消息@用户进行提醒。

                    要求：
                    1. 语气自然、像人聊天
                    2. 简短，不要啰嗦
                    3. 不要使用“提醒到了”、“请注意”等正式表达
                    4. 不要像系统通知
                    5. 可以稍微口语一点，但不要过度发挥

                    用户：${targetMention}
                    提醒内容：${task.content}
                """.trimIndent(),
                Feature(PSFeature.CHAT_URGENT or PSFeature.GRAB or PSFeature.FALLBACK)
            ) {
                callFallback {
                    val messages = buildMessageChain {
                        task.targetUserId?.let { +At(it.toLong()) }
                        +task.content
                    }
                    BotManage.getBot(task.botId)
                        .refBot
                        .getGroupOrFail(task.groupId.toLong())
                        .sendMessage(messages)
                }
                CoroutineScope(Job())
            }
        }

        scheduler!!.enqueueImmediateScan()

        // 注册工具集
        context.tool {
            {
                ReminderToolSet(store, wheel)
            }
        }

        // 启动调度器
        scheduler!!.start()
    }

    override fun onUnload() {
        scheduler?.stop()
    }
}