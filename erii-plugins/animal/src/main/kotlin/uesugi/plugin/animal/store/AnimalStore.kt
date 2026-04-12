package uesugi.plugin.animal.store

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uesugi.plugin.animal.domain.User
import uesugi.spi.Kv

class AnimalStore(private val kv: Kv) {

    private val log = KotlinLogging.logger {}

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    companion object {
        private const val KEY_PREFIX = "animal"
        private const val USER_PREFIX = "user"
        private const val USER_IDS_SUFFIX = "user_ids"
        private const val GROUP_IDS_KEY = "animal:group_ids"

        fun userKey(groupId: String, userId: Long): String = "$KEY_PREFIX:$USER_PREFIX:$groupId:$userId"
        private fun userIdsKey(groupId: String): String = "$KEY_PREFIX:$USER_IDS_SUFFIX:$groupId"
    }

    suspend fun saveUser(groupId: String, user: User) {
        val jsonStr = json.encodeToString(User.serializer(), user)
        kv.set(userKey(groupId, user.id), jsonStr)
        log.debug { "Saved user ${user.id} in group $groupId: $jsonStr" }
    }

    suspend fun getUser(groupId: String, userId: Long): User? {
        val jsonStr = kv.get(userKey(groupId, userId)) ?: return null
        return try {
            json.decodeFromString(User.serializer(), jsonStr)
        } catch (e: Exception) {
            log.error(e) { "Failed to deserialize user $userId in group $groupId" }
            null
        }
    }

    suspend fun deleteUser(groupId: String, userId: Long) {
        kv.delete(userKey(groupId, userId))
        log.debug { "Deleted user $userId in group $groupId" }
    }

    suspend fun getAllUsers(groupId: String): List<User> {
        val userIds = getAllUserIds(groupId)
        return userIds.mapNotNull { getUser(groupId, it) }
    }

    @Serializable
    data class UserIds(val ids: MutableSet<Long> = mutableSetOf())

    suspend fun getAllUserIds(groupId: String): Set<Long> {
        val jsonStr = kv.get(userIdsKey(groupId)) ?: return emptySet()
        return try {
            json.decodeFromString(UserIds.serializer(), jsonStr).ids
        } catch (e: Exception) {
            log.error(e) { "Failed to deserialize user ids for group $groupId" }
            emptySet()
        }
    }

    suspend fun addUserId(groupId: String, userId: Long) {
        val ids = getAllUserIds(groupId).toMutableSet()
        ids.add(userId)
        kv.set(userIdsKey(groupId), json.encodeToString(UserIds.serializer(), UserIds(ids)))
        addGroupId(groupId)
    }

    suspend fun removeUserId(groupId: String, userId: Long) {
        val ids = getAllUserIds(groupId).toMutableSet()
        ids.remove(userId)
        kv.set(userIdsKey(groupId), json.encodeToString(UserIds.serializer(), UserIds(ids)))
        removeGroupIdIfEmpty(groupId)
    }

    suspend fun getAllGroupIds(): Set<String> {
        val jsonStr = kv.get(GROUP_IDS_KEY) ?: return emptySet()
        return try {
            json.decodeFromString<GroupIds>(jsonStr).ids
        } catch (e: Exception) {
            log.error(e) { "Failed to deserialize group ids" }
            emptySet()
        }
    }

    @Serializable
    data class GroupIds(val ids: MutableSet<String> = mutableSetOf())

    suspend fun addGroupId(groupId: String) {
        val ids = getAllGroupIds().toMutableSet()
        ids.add(groupId)
        kv.set(GROUP_IDS_KEY, json.encodeToString(GroupIds(ids)))
    }

    suspend fun removeGroupIdIfEmpty(groupId: String) {
        val userIds = getAllUserIds(groupId)
        if (userIds.isEmpty()) {
            val ids = getAllGroupIds().toMutableSet()
            ids.remove(groupId)
            kv.set(GROUP_IDS_KEY, json.encodeToString(GroupIds(ids)))
            kv.delete(userIdsKey(groupId))
        }
    }
}
