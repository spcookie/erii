package uesugi.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uesugi.spi.Kv

@Serializable
data class ReminderIds(
    val ids: Set<String> = emptySet()
)

interface ReminderStore {
    suspend fun saveTask(task: ReminderTask)
    suspend fun deleteTask(botId: String, groupId: String, reminderId: String)
    suspend fun getTask(botId: String, groupId: String, reminderId: String): ReminderTask?
    suspend fun getAllTaskIds(botId: String, groupId: String): Set<String>
    suspend fun getAllActiveTasks(botId: String, groupId: String): List<ReminderTask>
    suspend fun registerBotGroup(botId: String, groupId: String)
    fun getAllBotGroupKeys(): List<BotGroupKey>
}

class ReminderStoreImpl(private val kv: Kv) : ReminderStore {

    companion object {
        private const val KEY_PREFIX = "reminder"
        private const val IDS_SUFFIX = "ids"

        private fun taskKey(botId: String, groupId: String, reminderId: String): String {
            return "$KEY_PREFIX:$botId:$groupId:$reminderId"
        }

        private fun idsKey(botId: String, groupId: String): String {
            return "$KEY_PREFIX:$botId:$groupId:$IDS_SUFFIX"
        }
    }

    private val json = Json

    override suspend fun saveTask(task: ReminderTask) {
        val taskJson = json.encodeToString(ReminderTask.serializer(), task)
        kv.set(taskKey(task.botId, task.groupId, task.reminderId), taskJson)

        val idsJson = kv.get(idsKey(task.botId, task.groupId))
        val ids = if (idsJson != null) {
            json.decodeFromString<ReminderIds>(idsJson).ids.toMutableSet()
        } else {
            mutableSetOf()
        }
        ids.add(task.reminderId)
        kv.set(idsKey(task.botId, task.groupId), json.encodeToString(ReminderIds.serializer(), ReminderIds(ids)))
    }

    override suspend fun getTask(botId: String, groupId: String, reminderId: String): ReminderTask? {
        val jsonStr = kv.get(taskKey(botId, groupId, reminderId)) ?: return null
        return json.decodeFromString(ReminderTask.serializer(), jsonStr)
    }

    override suspend fun deleteTask(botId: String, groupId: String, reminderId: String) {
        kv.delete(taskKey(botId, groupId, reminderId))

        val idsJson = kv.get(idsKey(botId, groupId))
        if (idsJson != null) {
            val ids = json.decodeFromString<ReminderIds>(idsJson).ids.toMutableSet()
            ids.remove(reminderId)
            kv.set(idsKey(botId, groupId), json.encodeToString(ReminderIds.serializer(), ReminderIds(ids)))
        }
    }

    override suspend fun getAllTaskIds(botId: String, groupId: String): Set<String> {
        val jsonStr = kv.get(idsKey(botId, groupId)) ?: return emptySet()
        return json.decodeFromString<ReminderIds>(jsonStr).ids
    }

    override suspend fun getAllActiveTasks(botId: String, groupId: String): List<ReminderTask> {
        val ids = getAllTaskIds(botId, groupId)
        return ids.mapNotNull { id ->
            getTask(botId, groupId, id)?.takeIf { it.status == ReminderStatus.ACTIVE }
        }
    }

    override fun getAllBotGroupKeys(): List<BotGroupKey> {
        return emptyList()
    }

    override suspend fun registerBotGroup(botId: String, groupId: String) {
        val idsJson = kv.get(idsKey(botId, groupId))
        if (idsJson == null) {
            kv.set(idsKey(botId, groupId), json.encodeToString(ReminderIds.serializer(), ReminderIds(emptySet())))
        }
    }
}
