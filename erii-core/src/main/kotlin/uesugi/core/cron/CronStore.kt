package uesugi.core.cron

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import uesugi.core.component.storage.MapDB

@Serializable
private data class CronTaskIds(val ids: Set<String> = emptySet())

@Serializable
private data class BotGroupKeys(val keys: Set<String> = emptySet())

class CronStore {

    companion object {
        private const val KEY_PREFIX = "cron"
        private const val IDS_SUFFIX = "ids"
        private const val BOT_GROUPS_KEY = "cron:bot_groups"
    }

    private val db: HTreeMap<String, String> by lazy {
        MapDB.Cache.hashMap("core_cron")
            .keySerializer(Serializer.STRING)
            .valueSerializer(Serializer.STRING)
            .createOrOpen()
    }

    private val json = Json

    private fun taskKey(botId: String, groupId: String, taskId: String) =
        "$KEY_PREFIX:$botId:$groupId:$taskId"

    private fun idsKey(botId: String, groupId: String) =
        "$KEY_PREFIX:$botId:$groupId:$IDS_SUFFIX"

    suspend fun saveTask(task: CronTask) = withContext(Dispatchers.IO) {
        db[taskKey(task.botId, task.groupId, task.taskId)] =
            json.encodeToString(CronTask.serializer(), task)

        val idsJson = db[idsKey(task.botId, task.groupId)]
        val ids = if (idsJson != null) {
            json.decodeFromString<CronTaskIds>(idsJson).ids.toMutableSet()
        } else {
            mutableSetOf()
        }
        ids.add(task.taskId)
        db[idsKey(task.botId, task.groupId)] =
            json.encodeToString(CronTaskIds.serializer(), CronTaskIds(ids))

        registerBotGroup(task.botId, task.groupId)
    }

    suspend fun getTask(botId: String, groupId: String, taskId: String): CronTask? =
        withContext(Dispatchers.IO) {
            db[taskKey(botId, groupId, taskId)]?.let {
                json.decodeFromString(CronTask.serializer(), it)
            }
        }

    suspend fun deleteTask(botId: String, groupId: String, taskId: String) =
        withContext(Dispatchers.IO) {
            db.remove(taskKey(botId, groupId, taskId))

            val idsJson = db[idsKey(botId, groupId)]
            if (idsJson != null) {
                val ids = json.decodeFromString<CronTaskIds>(idsJson).ids.toMutableSet()
                ids.remove(taskId)
                db[idsKey(botId, groupId)] =
                    json.encodeToString(CronTaskIds.serializer(), CronTaskIds(ids))
            }
        }

    suspend fun getAllTaskIds(botId: String, groupId: String): Set<String> =
        withContext(Dispatchers.IO) {
            db[idsKey(botId, groupId)]?.let {
                json.decodeFromString<CronTaskIds>(it).ids
            } ?: emptySet()
        }

    suspend fun getAllActiveTasks(botId: String, groupId: String): List<CronTask> {
        val ids = getAllTaskIds(botId, groupId)
        return ids.mapNotNull { id ->
            getTask(botId, groupId, id)?.takeIf { it.status == CronTaskStatus.ACTIVE }
        }
    }

    suspend fun getAllTasks(
        botId: String,
        groupId: String,
        offset: Int = 0,
        limit: Int = 0
    ): Pair<List<CronTask>, Int> {
        val ids = getAllTaskIds(botId, groupId)
        val sortedIds = ids.sorted()
        val total = sortedIds.size
        val pageIds = if (limit > 0) sortedIds.drop(offset).take(limit) else sortedIds
        val items = pageIds.mapNotNull { id -> getTask(botId, groupId, id) }
        return items to total
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
