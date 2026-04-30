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

    fun execute(id: String, onNotFound: (() -> Unit)? = null) {
        val action = tasks[id]
        if (action == null) {
            onNotFound?.invoke()
            error("Scheduled task not found: $id")
        }
        action()
    }
}
