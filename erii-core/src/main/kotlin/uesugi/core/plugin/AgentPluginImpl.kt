package uesugi.core.plugin

import ai.koog.prompt.executor.model.PromptExecutor
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.typesafe.config.Config
import io.ktor.client.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import kotlinx.coroutines.*
import net.mamoe.mirai.utils.ConcurrentHashMap
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.source
import org.jetbrains.exposed.v1.jdbc.Query
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.scheduling.JobScheduler
import org.mapdb.HTreeMap
import org.mapdb.Serializer
import uesugi.BotManage
import uesugi.common.*
import uesugi.core.component.EmbeddedVectorStore
import uesugi.core.component.LocalObjectStorage
import uesugi.core.component.MapDB
import uesugi.core.component.ObjectStorage
import uesugi.core.message.history.HistoryTable
import uesugi.core.message.resource.ResourceTable
import uesugi.core.route.MetaToolSetRegister
import uesugi.core.route.RouteCallEvent
import uesugi.spi.*
import uesugi.toolkit.EmbeddingUtil
import java.io.InputStream
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.toJavaDuration


internal class PluginDefImpl(
    override val name: String,
    override val routeKeys: List<RouteKey>
) : PluginDef

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

internal class BlobImpl(val defined: PluginDef) : Blob {

    private val default by lazy {
        LocalObjectStorage(
            baseDir = "./store/object/plugins".toPath().resolve(defined.name)
        )
    }

    override suspend fun get(path: Path): InputStream = withContext(Dispatchers.IO) {
        default.get(path).buffer().inputStream()
    }

    override suspend fun set(path: Path, value: InputStream) {
        withContext(Dispatchers.IO) {
            default.put(path, value.source())
        }
    }

    override suspend fun delete(path: Path) {
        withContext(Dispatchers.IO) {
            default.delete(path)
        }
    }

    override fun close() {
    }

}

internal class VectorImpl(val defined: PluginDef) : Vector {

    private val default by lazy {
        EmbeddedVectorStore(
            path = "./store/vector/plugins".toPath().resolve(defined.name).toNioPath(),
            dimension = 1024
        )
    }

    override suspend fun embedding(input: List<String>, images: List<ByteArray>): FloatArray {
        return EmbeddingUtil.embedding(input, images).first()
    }

    override suspend fun search(
        queryVector: FloatArray,
        topK: Int,
        filter: List<String>?
    ): List<Vector.SearchResult> {
        return default.search(queryVector, topK, filter).map {
            Vector.SearchResult(it.id, it.content, it.tag, it.score)
        }
    }

    override suspend fun upsert(id: String, content: String, tag: String, vector: FloatArray) {
        default.upsert(id, content, tag, vector)
    }

    override suspend fun delete(id: String) {
        default.delete(id)
    }

    override suspend fun deleteAll() {
        default.deleteAll()
    }

    override fun close() {
        default.close()
    }
}

internal class ConfigImpl(val plugin: AgentExtension) : PluginConfig {

    override suspend fun read(path: String): InputStream {
        return withContext(Dispatchers.IO) {
            val path = Paths.get(path)
            val normalize = Paths.get("/plugin")
                .resolve(Paths.get(plugin::class.simpleName!!))
                .resolve(path)
                .normalize()
                .toString()
            plugin.javaClass
                .getResourceAsStream(normalize)
                ?: error("Config not found: $normalize")
        }
    }

    /**
     * 获取插件的 Typesafe Config 对象
     * 支持从以下位置读取配置：
     * 1. -Dplugin.{PluginName}.config 参数指定的文件
     * 2. -Dconfig.plugin.dir 参数指定的目录下的 {PluginName}.conf 文件
     * 3. classpath 中的 /plugin/{PluginName}.conf 文件
     * 4. 主配置文件 application.conf 中的 plugins.{PluginName} 部分
     */
    override fun getPluginConfig(): Config {
        val pluginName = plugin::class.simpleName ?: error("Plugin name not found")
        return ConfigHolder.getPluginConfig(plugin.javaClass, pluginName)
    }

}

internal class MetaImpl(
    override val botId: String,
    override val groupId: String,
    override val roledBot: IBotManage.RoledBot,
    override val senderId: String? = null,
    override val input: String? = null,
    override val echo: String? = null
) : Meta

internal class DatabaseImpl : Database {
    override suspend fun getHistory(query: () -> Query): List<HistoryRecord> {
        val storage by ref<ObjectStorage>()
        return withContext(Dispatchers.IO) {
            transaction {
                query().map {
                    HistoryRecord(
                        id = it[HistoryTable.id].value,
                        botMark = it[HistoryTable.botMark],
                        groupId = it[HistoryTable.groupId],
                        userId = it[HistoryTable.userId],
                        nick = it[HistoryTable.nick],
                        messageType = it[HistoryTable.messageType],
                        content = it[HistoryTable.content],
                        resource = if (it.getOrNull(ResourceTable.id) != null) {
                            val url = it[ResourceTable.url]
                            val bytes = storage.get(url.toPath()).use { source -> source.buffer().readByteArray() }
                            ResourceRecord(
                                id = it[ResourceTable.id].value,
                                botMark = it[ResourceTable.botMark],
                                groupId = it[ResourceTable.groupId],
                                url = url,
                                fileName = it[ResourceTable.fileName],
                                size = it[ResourceTable.size],
                                md5 = it[ResourceTable.md5],
                                createdAt = it[ResourceTable.createdAt],
                                bytes = bytes
                            )
                        } else {
                            null
                        },
                        createdAt = it[HistoryTable.createdAt]
                    )
                }
            }
        }

    }

}

class ServerImpl(val defined: PluginDef) : Server {

    private val routeing by lazy {
        var ref: Route? = null
        embeddedServer(Netty, configure = {
            connectors.add(EngineConnectorBuilder().apply {
                host = "127.0.0.1"
                port = 8888
            })
            connectionGroupSize = 2
            workerGroupSize = 5
            callGroupSize = 10
        }) {
            install(ContentNegotiation) {
                jackson()
            }

            routing {
                route("/plugin") {
                    ref = this
                }
            }
        }.start()
        ref!!
    }

    override fun route(conf: Route.() -> Unit) {
        routeing.route("/${defined.name}") {
            conf()
        }
    }

}

class PluginContextImpl(
    override val defined: PluginDef,
    override val mem: Mem,
    override val kv: Kv,
    override val blob: Blob,
    override val vector: Vector,
    override val config: PluginConfig,
    override val database: Database,
    override val scheduler: JobScheduler,
    override val llm: PromptExecutor,
    override val http: HttpClient,
    override val server: Server,
    val httpProxy: HttpClient
) : PluginContextBootstrap {

    companion object {
        val log = logger()
    }

    private val scope =
        CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("ImageCreator") + CoroutineExceptionHandler { _, exception ->
            log.error("Plugin context detected error: {}", exception.message, exception)
        })

    private lateinit var job: Job

    private val handlers = mutableListOf<Handler>()
    private val toolsets = mutableListOf<MetaToolSetCreator>()

    override val HttpClient.proxy: HttpClient
        get() = httpProxy

    override fun chain(handler: Handler) {
        handlers += handler
    }

    override fun tool(toolset: PluginContext.() -> MetaToolSetCreator) {
        toolsets += toolset(this)
    }

    override fun open() {
        job = EventBus.subscribeAsync<RouteCallEvent>(scope) { event ->
            for (rk in defined.routeKeys) {
                if (event hit rk.key) {
                    val meta = MetaImpl(
                        botId = event.botId,
                        groupId = event.groupId,
                        roledBot = BotManage.getBot(event.botId),
                        input = event.input,
                        senderId = event.senderId,
                        echo = event.echo
                    )
                    for (handler in handlers) {
                        handler(meta)
                    }
                }
            }
        }
    }

    override fun ready() {
        for (toolset in toolsets) {
            MetaToolSetRegister.addToolSet(defined.name, toolset)
        }
    }

    override fun close() {
        if (this::job.isInitialized) {
            EventBus.unsubscribeAsync(job)
        }
    }

}