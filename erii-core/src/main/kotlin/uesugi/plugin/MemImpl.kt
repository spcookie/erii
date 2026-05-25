package uesugi.plugin

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import uesugi.spi.ExpireStrategy
import uesugi.spi.Mem
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.toJavaDuration

internal class MemImpl : Mem {

    private val default by lazy { Caffeine.newBuilder().build<String, String>() }

    private val map = ConcurrentHashMap<String, Cache<String, String>>()

    override suspend fun get(key: String) = default.getIfPresent(key)

    override suspend fun set(key: String, value: String) {
        default.put(key, value)
    }

    override suspend fun set(
        key: String,
        value: String,
        expire: Duration,
        strategy: ExpireStrategy
    ) {
        map.getOrPut(strategy.name + "_" + expire.toString()) {
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
        default.invalidateAll()
    }

}
