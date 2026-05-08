package uesugi.core.plugin

import java.util.concurrent.ConcurrentHashMap

object SchedulerBridge {
    private val tasks = ConcurrentHashMap<String, () -> Unit>()
    private val onMissingHandlers = ConcurrentHashMap<String, () -> Unit>()

    fun register(id: String, action: () -> Unit, onMissing: (() -> Unit)? = null) {
        tasks[id] = action
        onMissing?.let { onMissingHandlers[id] = it }
    }

    fun unregister(id: String) {
        tasks.remove(id)
        onMissingHandlers.remove(id)
    }

    fun contains(id: String): Boolean = tasks.containsKey(id)

    /** 供 JobRunr 调度调用，内部处理 task 不存在时的清理逻辑 */
    fun runTask(id: String) {
        val action = tasks[id]
        if (action != null) {
            action()
        } else {
            onMissingHandlers.remove(id)?.invoke()
        }
    }
}
