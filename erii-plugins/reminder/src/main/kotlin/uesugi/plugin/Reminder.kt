package uesugi.plugin

import kotlinx.coroutines.runBlocking
import org.pf4j.Extension
import uesugi.spi.AgentExtension
import uesugi.spi.AgentPlugin
import uesugi.spi.PluginContext
import uesugi.spi.PluginDefinition

@PluginDefinition
class Reminder : AgentPlugin()

@Extension
class ReminderExtension : AgentExtension<Reminder> {

    private var scheduler: ReminderScheduler? = null

    override fun onLoad(context: PluginContext) {
        val store = ReminderStoreImpl(context.kv)
        val wheel = ReminderWheel(store)

        runBlocking { wheel.init() }

        scheduler = ReminderScheduler(context.scheduler, wheel) {
            val targetMention = task.targetUserId?.let { "@$it " } ?: ""
            "$targetMention${task.content}"

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