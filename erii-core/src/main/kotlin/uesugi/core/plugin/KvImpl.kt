package uesugi.core.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.mamoe.mirai.utils.ConcurrentHashMap
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import uesugi.core.component.MapDB
import uesugi.spi.ExpireStrategy
import uesugi.spi.Kv
import uesugi.spi.PluginDef
import kotlin.time.Duration

internal class KvImpl(val defined: PluginDef) : Kv {

    private val default by lazy {
        MapDB.Cache.hashMap(defined.name)
            .keySerializer(Serializer.STRING)
            .valueSerializer(Serializer.STRING)
            .createOrOpen()
    }

    private val map = ConcurrentHashMap<String, HTreeMap<String, String>>()

    override suspend fun get(key: String): String? {
        return withContext(Dispatchers.IO) {
            default[key]
        }
    }

    override suspend fun set(key: String, value: String) {
        withContext(Dispatchers.IO) {
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
            map.getOrPut(strategy.name + "_" + expire.toString()) {
                MapDB.Cache.hashMap(defined.name)
                    .keySerializer(Serializer.STRING)
                    .valueSerializer(Serializer.STRING)
                    .apply {
                        when (strategy) {
                            ExpireStrategy.AFTER_WRITE -> expireAfterCreate(expire.inWholeMilliseconds)
                            ExpireStrategy.AFTER_ACCESS -> expireAfterGet(expire.inWholeMilliseconds)
                        }
                    }
                    .createOrOpen()
            }[key] = value
        }
    }

    override suspend fun delete(key: String) {
        withContext(Dispatchers.IO) {
            map.forEach { (_, cache) ->
                cache.remove(key)
            }
            default.remove(key)
        }
    }

    override fun close() {
        map.forEach { (_, cache) ->
            cache.close()
        }
        default.close()
    }

}
