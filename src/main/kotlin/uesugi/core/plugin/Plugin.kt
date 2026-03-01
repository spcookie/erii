package uesugi.core.plugin

import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.executor.model.PromptExecutor
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.ktor.client.*
import kotlinx.cli.ArgParser
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
import uesugi.core.*
import uesugi.core.message.history.HistoryRecord
import uesugi.core.message.history.HistoryTable
import uesugi.core.message.resource.ResourceRecord
import uesugi.core.message.resource.ResourceTable
import uesugi.core.route.MetaToolSetRegister
import uesugi.core.route.RouteCallEvent
import uesugi.toolkit.*
import java.io.InputStream
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface Plugin {
    val name: String
    fun onLoad(context: PluginContext)
    fun onUnload() {}
}

interface CmdPlugin : Plugin {
    val argParser: ArgParser
}

interface RoutePlugin : Plugin {
    val matcher: Pair<String, String>
}

interface PassivePlugin : Plugin, ClassNameMixin

interface ClassNameMixin : Plugin {
    override val name: String
        get() = this::class.simpleName!!
}

typealias Handler = suspend PluginContext.(meta: Meta) -> Unit
typealias MetaToolSetCreator = () -> MetaToolSet

interface PluginContext : AutoCloseable {
    val defined: PluginDef

    val mem: Mem
    val kv: Kv
    val blob: Blob

    val database: Database

    val scheduler: JobScheduler

    val llm: PromptExecutor
    val http: HttpClient

    val HttpClient.proxy: HttpClient

    fun chain(handler: Handler)

    fun tool(toolset: PluginContext.() -> MetaToolSetCreator)

    fun open()
}

interface MetaToolSet : ToolSet {
    companion object {
        lateinit var meta: Meta
    }
}

interface PluginDef {
    val name: String
    val routeKeys: List<RouteKey>
}

sealed interface RouteKey {
    val key: String
}

class LLMRouteKey(override val key: String) : RouteKey

class CmdRouteKey(override val key: String) : RouteKey

enum class ExpireStrategy {
    AFTER_WRITE,
    AFTER_ACCESS
}

interface Mem {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun set(key: String, value: String, expire: Duration, strategy: ExpireStrategy)
    fun delete(key: String)
}

interface Kv {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun set(key: String, value: String, expire: Duration, strategy: ExpireStrategy)
    fun delete(key: String)
}

interface Blob {
    fun get(path: Path): InputStream
    fun set(path: Path, value: InputStream)
    fun delete(path: Path)
}

interface Meta {
    val botId: String
    val groupId: String
    val roledBot: BotManage.RoledBot
    val input: String?
    val senderId: String?
    val echo: String?
}

interface Database {
    suspend fun getHistory(query: () -> Query): List<HistoryRecord>
}

fun Meta.sendAgent(
    input: String,
    state: SendAgentState
) = sendAgent(input, SendAgentConf(), state)

fun Meta.sendAgent(
    input: String,
    conf: SendAgentConf = SendAgentConf(),
    state: SendAgentState
) = sendAgent(botId, groupId, input, conf, state)

interface SendAgentState {
    val scope: CoroutineScope
    fun sendBefore(sentences: List<String>) {}
    fun sendAfter(sentences: List<String>) {}
    fun sendReplay(sentence: String) {}
    fun sendClosed() {}
    fun sendFinally() {}
    fun dispatchReject() {}
    fun dispatchFallback() {}
    fun callStart() {}
    fun callCompletion() {}
}

@OptIn(ExperimentalUuidApi::class)
data class SendAgentConf(
    val chatPointRule: String? = null,
    val webSearch: Boolean = false,
    val toolSets: ((ChatToolSet) -> List<ToolSet>)? = null,
    val flag: ProactiveSpeakFeatureFlag = ProactiveSpeakFeature.NONE,
    val echo: String = Uuid.random().toHexString(),
)

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    state: SendAgentState
) = sendAgent(botId, groupId, input, SendAgentConf(), state)

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    conf: SendAgentConf = SendAgentConf(),
    state: SendAgentState
) {
    val (chatPointRule, webSearch, toolSets, flag, echo) = conf
    val lifeCycleRef = mutableListOf<(AgentSendLifeCycleEvent) -> Unit>()
    val lifeCycleSubscriber: (AgentSendLifeCycleEvent) -> Unit = { event ->
        val lifeCycleEvent = event.takeIf { event.botId == botId && event.groupId == groupId && event.echo == echo }
        if (lifeCycleEvent != null) {
            when (lifeCycleEvent) {
                is AgentBeforeSendAndReceiveEvent -> state.sendBefore(lifeCycleEvent.sentences)
                is AgentAfterSendAndReceiveEvent -> state.sendAfter(lifeCycleEvent.sentences)
                is AgentReceiveReplyEvent -> state.sendReplay(lifeCycleEvent.sentence)
                is AgentSendAndReceiveClosedEvent -> state.sendClosed()
                is AgentSendAndReceiveFinallyEvent -> state.sendFinally()
            }
        }
    }
    lifeCycleRef += lifeCycleSubscriber
    EventBus.subscribeSync<AgentSendLifeCycleEvent>(lifeCycleSubscriber)

    val dispatchRef = mutableListOf<Job>()
    val dispatchSubscriber: suspend (AgentDispatchEvent) -> Unit = { event ->
        val dispatchEvent = event.takeIf { event.botId == botId && event.groupId == groupId && event.echo == echo }
        if (dispatchEvent != null) {
            when (dispatchEvent) {
                is AgentRejectGrabEvent -> {
                    state.dispatchReject()
                }

                is AgentFallbackEvent -> {
                    state.dispatchFallback()
                }

                is AgentCallStartEvent -> {
                    state.callStart()
                }

                is AgentCallCompletionEvent -> {
                    try {
                        state.callCompletion()
                    } finally {
                        dispatchRef.forEach { EventBus.unsubscribeAsync(it) }
                        lifeCycleRef.forEach { EventBus.unsubscribeSync<AgentSendLifeCycleEvent>(it) }
                    }
                }
            }
        }
    }
    dispatchRef += EventBus.subscribeAsync<AgentDispatchEvent>(state.scope, dispatchSubscriber)

    EventBus.postAsync(
        ProactiveSpeakEvent(
            botId = botId,
            _groupId = groupId,
            impulse = 0.0,
            interruptionMode = InterruptionMode.Interrupt,
            input = input,
            chatPointRule = chatPointRule,
            webSearch = webSearch,
            toolSets = toolSets,
            flag = flag,
            echo = echo
        )
    )

}

internal class PluginDefImpl(
    override val name: String,
    override val routeKeys: List<RouteKey>
) : PluginDef

internal class MemImpl : Mem {

    private val default by lazy { Caffeine.newBuilder().build<String, String>() }

    private val map = ConcurrentHashMap<String, Cache<String, String>>()

    override fun get(key: String) = default.getIfPresent(key)

    override fun set(key: String, value: String) {
        default.put(key, value)
    }

    override fun set(
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

    override fun delete(key: String) {
        map.forEach { (_, cache) ->
            cache.invalidate(key)
        }
        default.invalidate(key)
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

    override fun get(key: String): String? {
        return default[key]
    }

    override fun set(key: String, value: String) {
        default[key] = value
    }

    override fun set(
        key: String,
        value: String,
        expire: Duration,
        strategy: ExpireStrategy
    ) {
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

    override fun delete(key: String) {
        map.forEach { (_, cache) ->
            cache.remove(key)
        }
        default.remove(key)
    }

}

internal class BlobImpl(val defined: PluginDef) : Blob {

    private val default by lazy {
        LocalFileStorage(
            baseDir = "./store/object/plugins".toPath().resolve(defined.name)
        )
    }

    override fun get(path: Path): InputStream = default.get(path).buffer().inputStream()

    override fun set(path: Path, value: InputStream) {
        default.put(path, value.source())
    }

    override fun delete(path: Path) {
        default.delete(path)
    }

}

internal class MetaImpl(
    override val botId: String,
    override val groupId: String,
    override val roledBot: BotManage.RoledBot,
    override val senderId: String? = null,
    override val input: String? = null,
    override val echo: String? = null
) : Meta

internal class DatabaseImpl : Database {
    override suspend fun getHistory(query: () -> Query): List<HistoryRecord> {
        val storage by ref<FileStorage>()
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

class PluginContextImpl(
    override val defined: PluginDef,
    override val mem: Mem,
    override val kv: Kv,
    override val blob: Blob,
    override val database: Database,
    override val scheduler: JobScheduler,
    override val llm: PromptExecutor,
    override val http: HttpClient,
    val httpProxy: HttpClient
) : PluginContext {

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
        for (toolset in toolsets) {
            MetaToolSetRegister.addToolSet(defined.name, toolset)
        }
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

    override fun close() {
        if (this::job.isInitialized) {
            EventBus.unsubscribeAsync(job)
        }
    }

}