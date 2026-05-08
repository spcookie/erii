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

    /** 内存缓存，避免每次读写 KV */
    private var memoryCache: TodayCache? = null

    fun loadPigList(pigs: List<PigData>) {
        pigList = pigs
    }

    fun getPigList(): List<PigData> = pigList

    /**
     * 获取今天的缓存。
     * 优先读内存缓存；若内存为空或日期不是今天，则从 KV 读取并校验日期。
     */
    suspend fun getTodayCache(todayDate: String): TodayCache {
        memoryCache?.let { cache ->
            if (cache.date == todayDate) {
                return cache
            }
        }

        val cached = kv.get(todayCacheKey)?.let {
            runCatching {
                JSON.decodeFromString<TodayCache>(it)
            }.getOrNull()
        }

        return if (cached != null && cached.date == todayDate) {
            memoryCache = cached
            cached
        } else {
            val empty = TodayCache(todayDate, emptyMap())
            memoryCache = empty
            empty
        }
    }

    suspend fun saveTodayCache(cache: TodayCache) {
        memoryCache = cache
        kv.set(todayCacheKey, JSON.encodeToString(TodayCache.serializer(), cache))
    }

    suspend fun getUserPig(userId: String, todayDate: String): PigData? {
        return getTodayCache(todayDate).records[userId]
    }

    suspend fun setUserPig(userId: String, pig: PigData, todayDate: String) {
        val cache = getTodayCache(todayDate)
        val newRecords = cache.records.toMutableMap()
        newRecords[userId] = pig
        saveTodayCache(cache.copy(records = newRecords))
    }

    /** 清空今日缓存（定时任务调用） */
    suspend fun clearTodayCache() {
        memoryCache = null
        kv.delete(todayCacheKey)
    }
}
