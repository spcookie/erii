package uesugi.plugin

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class ReminderWheel(
    private val store: ReminderStore
) {
    // 按 botId-groupId 分组，每组一个待触发队列
    private val wheel = ConcurrentHashMap<BotGroupKey, ConcurrentLinkedQueue<ReminderTask>>()

    // 维护注册的 bot-group 组合
    private val registeredKeys = ConcurrentHashMap.newKeySet<BotGroupKey>()

    fun register(botId: String, groupId: String) {
        val key = BotGroupKey(botId, groupId)
        registeredKeys.add(key)
        wheel.getOrPut(key) { ConcurrentLinkedQueue() }
    }

    suspend fun pushTask(task: ReminderTask) {
        val key = BotGroupKey(task.botId, task.groupId)
        wheel.getOrPut(key) { ConcurrentLinkedQueue() }.add(task)
        store.saveTask(task)
    }

    fun getAndClearDueTasks(botId: String, groupId: String, now: Long): List<ReminderTask> {
        val key = BotGroupKey(botId, groupId)
        val queue = wheel[key] ?: return emptyList()

        val dueTasks = mutableListOf<ReminderTask>()
        val iterator = queue.iterator()

        while (iterator.hasNext()) {
            val task = iterator.next()
            if (task.triggerTime <= now) {
                iterator.remove()
                dueTasks.add(task)
            }
        }

        return dueTasks
    }

    fun getRegisteredKeys(): Set<BotGroupKey> = registeredKeys.toSet()

    fun clear(botId: String, groupId: String) {
        val key = BotGroupKey(botId, groupId)
        wheel.remove(key)?.clear()
    }

    suspend fun removeTask(task: ReminderTask) {
        val key = BotGroupKey(task.botId, task.groupId)
        wheel[key]?.remove(task)
        store.deleteTask(task.botId, task.groupId, task.reminderId)
    }
}