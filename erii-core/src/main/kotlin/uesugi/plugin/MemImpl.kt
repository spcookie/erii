package uesugi.plugin

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import uesugi.spi.ExpireStrategy
import uesugi.spi.Mem
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.toJavaDuration

internal class MemImpl : Mem {

    private val defaultDelegate = lazy { Caffeine.newBuilder().build<String, String>() }
    private val default by defaultDelegate

    private val map = ConcurrentHashMap<String, Cache<String, String>>()

    override suspend fun get(key: String): String? {
        return default.getIfPresent(key) ?: map.values.firstNotNullOfOrNull { cache ->
            cache.getIfPresent(key)
        }
    }

    override suspend fun set(key: String, value: String) {
        map.values.forEach { cache ->
            cache.invalidate(key)
        }
        default.put(key, value)
    }

    override suspend fun set(
        key: String,
        value: String,
        expire: Duration,
        strategy: ExpireStrategy
    ) {
        default.invalidate(key)
        val cacheKey = strategy.name + "_" + expire.inWholeMilliseconds
        map.forEach { (bucket, cache) ->
            if (bucket != cacheKey) {
                cache.invalidate(key)
            }
        }
        map.getOrPut(cacheKey) {
            Caffeine.newBuilder()
                .apply {
                    when (strategy) {
                        ExpireStrategy.AFTER_WRITE -> expireAfterWrite(expire.toJavaDuration())
                        ExpireStrategy.AFTER_ACCESS -> expireAfterAccess(expire.toJavaDuration())
                    }
                }
                .build()
        }.put(key, value)
    }

    override suspend fun delete(key: String) {
        map.forEach { (_, cache) ->
            cache.invalidate(key)
        }
        default.invalidate(key)
    }

    override fun close() {
        map.forEach { (_, cache) ->
            cache.invalidateAll()
        }
        if (defaultDelegate.isInitialized()) {
            default.invalidateAll()
        }
    }

}
