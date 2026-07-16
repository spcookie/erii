package uesugi.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import uesugi.core.component.storage.MapDB
import uesugi.spi.ExpireStrategy
import uesugi.spi.Kv
import uesugi.spi.PluginDef
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

internal class KvImpl(val defined: PluginDef) : Kv {

    private companion object {
        const val DEFAULT_MAP = "kv"
        const val EXPIRE_BUCKETS_MAP = "kv_expire_buckets"
    }

    private val defaultDelegate = lazy {
        MapDB.plugin(defined.name).hashMap(DEFAULT_MAP)
            .keySerializer(Serializer.STRING)
            .valueSerializer(Serializer.STRING)
            .createOrOpen()
    }
    private val default by defaultDelegate

    private val bucketIndexDelegate = lazy {
        MapDB.plugin(defined.name).hashMap(EXPIRE_BUCKETS_MAP)
            .keySerializer(Serializer.STRING)
            .valueSerializer(Serializer.STRING)
            .createOrOpen()
    }
    private val bucketIndex by bucketIndexDelegate

    private val map = ConcurrentHashMap<String, HTreeMap<String, String>>()

    override suspend fun get(key: String): String? {
        return withContext(Dispatchers.IO) {
            default[key] ?: bucketKeys().firstNotNullOfOrNull { bucketKey ->
                bucket(bucketKey)[key]
            }
        }
    }

    override suspend fun set(key: String, value: String) {
        withContext(Dispatchers.IO) {
            bucketKeys().forEach { bucket(it).remove(key) }
            default[key] = value
        }
    }

    override suspend fun set(
        key: String,
        value: String,
        expire: Duration,
        strategy: ExpireStrategy
    ) {
        withContext(Dispatchers.IO) {
            default.remove(key)
            val bucketKey = bucketKey(strategy, expire)
            bucketIndex[bucketKey] = "1"
            bucket(bucketKey)[key] = value
        }
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) {
            bucketKeys().forEach { bucket(it).remove(key) }
            default.remove(key)
        }
    }

    override fun close() {
        map.forEach { (_, cache) ->
            cache.close()
        }
        if (defaultDelegate.isInitialized()) {
            default.close()
        }
        if (bucketIndexDelegate.isInitialized()) {
            bucketIndex.close()
        }
    }

    private fun bucket(bucketKey: String): HTreeMap<String, String> {
        return map.getOrPut(bucketKey) {
            val (strategy, millis) = parseBucketKey(bucketKey)
            MapDB.plugin(defined.name).hashMap("kv_$bucketKey")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.STRING)
                .apply {
                    when (strategy) {
                        ExpireStrategy.AFTER_WRITE -> expireAfterCreate(millis)
                        ExpireStrategy.AFTER_ACCESS -> expireAfterGet(millis)
                    }
                }
                .createOrOpen()
        }
    }

    private fun bucketKeys(): Set<String> = buildSet {
        addAll(map.keys)
        addAll(bucketIndex.keys)
    }

    private fun bucketKey(strategy: ExpireStrategy, expire: Duration): String {
        return "${strategy.name}_${expire.inWholeMilliseconds}"
    }

    private fun parseBucketKey(bucketKey: String): Pair<ExpireStrategy, Long> {
        val splitAt = bucketKey.lastIndexOf('_')
        require(splitAt > 0 && splitAt < bucketKey.length - 1) { "Invalid kv bucket key: $bucketKey" }
        val strategy = ExpireStrategy.valueOf(bucketKey.substring(0, splitAt))
        val millis = bucketKey.substring(splitAt + 1).toLong()
        return strategy to millis
    }

}
