package uesugi.core.reminder

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import uesugi.core.component.storage.MapDB

@Serializable
private data class ReminderIds(val ids: Set<String> = emptySet())

@Serializable
private data class BotGroupKeys(val keys: Set<String> = emptySet())

class ReminderStore {

    companion object {
        private const val KEY_PREFIX = "reminder"
        private const val IDS_SUFFIX = "ids"
        private const val BOT_GROUPS_KEY = "reminder:bot_groups"
    }

    private val db: HTreeMap<String, String> by lazy {
        MapDB.Cache.hashMap("core_reminder")
            .keySerializer(Serializer.STRING)
            .valueSerializer(Serializer.STRING)
            .createOrOpen()
    }

    private val json = Json

    private fun taskKey(botId: String, groupId: String, reminderId: String) =
        "$KEY_PREFIX:$botId:$groupId:$reminderId"

    private fun idsKey(botId: String, groupId: String) =
        "$KEY_PREFIX:$botId:$groupId:$IDS_SUFFIX"

    suspend fun saveTask(task: ReminderTask) = withContext(Dispatchers.IO) {
        db[taskKey(task.botId, task.groupId, task.reminderId)] =
            json.encodeToString(ReminderTask.serializer(), task)

        val idsJson = db[idsKey(task.botId, task.groupId)]
        val ids = if (idsJson != null) {
            json.decodeFromString<ReminderIds>(idsJson).ids.toMutableSet()
        } else {
            mutableSetOf()
        }
        ids.add(task.reminderId)
        db[idsKey(task.botId, task.groupId)] =
            json.encodeToString(ReminderIds.serializer(), ReminderIds(ids))

        registerBotGroup(task.botId, task.groupId)
    }

    suspend fun getTask(botId: String, groupId: String, reminderId: String): ReminderTask? =
        withContext(Dispatchers.IO) {
            db[taskKey(botId, groupId, reminderId)]?.let {
                json.decodeFromString(ReminderTask.serializer(), it)
            }
        }

    suspend fun deleteTask(botId: String, groupId: String, reminderId: String) =
        withContext(Dispatchers.IO) {
            db.remove(taskKey(botId, groupId, reminderId))

            val idsJson = db[idsKey(botId, groupId)]
            if (idsJson != null) {
                val ids = json.decodeFromString<ReminderIds>(idsJson).ids.toMutableSet()
                ids.remove(reminderId)
                db[idsKey(botId, groupId)] =
                    json.encodeToString(ReminderIds.serializer(), ReminderIds(ids))
            }
        }

    suspend fun getAllTaskIds(botId: String, groupId: String): Set<String> =
        withContext(Dispatchers.IO) {
            db[idsKey(botId, groupId)]?.let {
                json.decodeFromString<ReminderIds>(it).ids
            } ?: emptySet()
        }

    suspend fun getAllActiveTasks(botId: String, groupId: String): List<ReminderTask> {
        val ids = getAllTaskIds(botId, groupId)
        return ids.mapNotNull { id ->
            getTask(botId, groupId, id)?.takeIf { it.status == ReminderStatus.ACTIVE }
        }
    }

    suspend fun getAllBotGroupKeys(): List<BotGroupKey> = withContext(Dispatchers.IO) {
        val json2 = db[BOT_GROUPS_KEY] ?: return@withContext emptyList()
        json.decodeFromString<BotGroupKeys>(json2).keys.map { key ->
            val parts = key.split(":", limit = 2)
            BotGroupKey(parts[0], parts[1])
        }
    }

    private fun registerBotGroup(botId: String, groupId: String) {
        val keysJson = db[BOT_GROUPS_KEY]
        val keys = if (keysJson != null) {
            json.decodeFromString<BotGroupKeys>(keysJson).keys.toMutableSet()
        } else {
            mutableSetOf()
        }
        val keyStr = "$botId:$groupId"
        if (keys.add(keyStr)) {
            db[BOT_GROUPS_KEY] = json.encodeToString(BotGroupKeys.serializer(), BotGroupKeys(keys))
        }
    }
}
