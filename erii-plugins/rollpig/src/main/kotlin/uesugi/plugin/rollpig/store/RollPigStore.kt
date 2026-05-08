package uesugi.plugin.rollpig.store

import kotlinx.serialization.Serializable
import uesugi.common.toolkit.JSON
import uesugi.spi.Kv

@Serializable
data class PigData(
    val id: String,
    val name: String,
    val description: String,
    val analysis: String
)

@Serializable
data class TodayCache(
    val date: String,
    val records: Map<String, PigData>
)

class RollPigStore(
    private val kv: Kv
) {
    private val todayCacheKey = "rollpig_today"

    private var pigList: List<PigData> = emptyList()

    fun loadPigList(pigs: List<PigData>) {
        pigList = pigs
    }

    fun getPigList(): List<PigData> = pigList

    suspend fun getTodayCache(): TodayCache {
        return kv.get(todayCacheKey)?.let {
            runCatching {
                JSON.decodeFromString<TodayCache>(it)
            }.getOrNull()
        } ?: TodayCache("", emptyMap())
    }

    suspend fun saveTodayCache(cache: TodayCache) {
        kv.set(todayCacheKey, JSON.encodeToString(TodayCache.serializer(), cache))
    }

    suspend fun getUserPig(userId: String): PigData? {
        return getTodayCache().records[userId]
    }

    suspend fun setUserPig(userId: String, pig: PigData) {
        val cache = getTodayCache()
        val newRecords = cache.records.toMutableMap()
        newRecords[userId] = pig
        saveTodayCache(cache.copy(records = newRecords))
    }
}