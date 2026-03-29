package uesugi.core.plugin

import java.util.concurrent.ConcurrentHashMap

object SchedulerBridge {
    private val tasks = ConcurrentHashMap<String, () -> Unit>()

    fun register(id: String, action: () -> Unit) {
        tasks[id] = action
    }

    fun unregister(id: String) {
        tasks.remove(id)
    }

    /** JobRunr 调用入口 - 在主 ClassLoader 中 */
    fun execute(id: String) {
        val action = tasks[id] ?: error("Scheduled task not found: $id")
        action()
    }
}
