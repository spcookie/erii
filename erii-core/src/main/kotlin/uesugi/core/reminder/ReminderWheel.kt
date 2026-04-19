package uesugi.core.reminder

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

class ReminderWheel(private val store: ReminderStore) {

    private val wheel = ConcurrentHashMap<BotGroupKey, ConcurrentLinkedQueue<ReminderTask>>()
    private val registeredKeys = ConcurrentHashMap.newKeySet<BotGroupKey>()

    suspend fun init() {
        store.getAllBotGroupKeys().forEach { key ->
            register(key.botId, key.groupId)
            store.getAllActiveTasks(key.botId, key.groupId).forEach { task ->
                enqueue(task)
            }
        }
    }

    fun register(botId: String, groupId: String) {
        val key = BotGroupKey(botId, groupId)
        registeredKeys.add(key)
        wheel.getOrPut(key) { ConcurrentLinkedQueue() }
    }

    suspend fun pushTask(task: ReminderTask) {
        val key = BotGroupKey(task.botId, task.groupId)
        registeredKeys.add(key)
        wheel.getOrPut(key) { ConcurrentLinkedQueue() }.add(task)
        store.saveTask(task)
    }

    private fun enqueue(task: ReminderTask) {
        val key = BotGroupKey(task.botId, task.groupId)
        wheel.getOrPut(key) { ConcurrentLinkedQueue() }.add(task)
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

    suspend fun removeTask(task: ReminderTask) {
        val key = BotGroupKey(task.botId, task.groupId)
        wheel[key]?.remove(task)
        store.deleteTask(task.botId, task.groupId, task.reminderId)
    }
}
