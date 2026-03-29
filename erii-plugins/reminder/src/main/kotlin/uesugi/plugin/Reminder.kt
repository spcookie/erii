package uesugi.plugin

import org.pf4j.Extension
import uesugi.spi.AgentExtension
import uesugi.spi.AgentPlugin
import uesugi.spi.PluginContext
import uesugi.spi.PluginDefinition

@PluginDefinition
class Reminder : AgentPlugin()

@Extension
class ReminderExtension : AgentExtension<Reminder> {

    private var store: ReminderStore? = null
    private var wheel: ReminderWheel? = null
    private var scheduler: ReminderScheduler? = null

    override fun onLoad(context: PluginContext) {
        store = ReminderStoreImpl(context.kv)
        wheel = ReminderWheel(store!!)
        scheduler = ReminderScheduler(context.scheduler, wheel!!, context)

        // 注册工具集
        context.tool {
            {
                ReminderToolSet(store!!, wheel!!)
            }
        }

        // 启动调度器
        scheduler!!.start()
    }

    override fun onUnload() {
        scheduler?.stop()
    }
}