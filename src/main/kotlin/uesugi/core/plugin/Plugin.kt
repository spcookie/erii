package uesugi.core.plugin

import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.prompt.executor.model.PromptExecutor
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.PrintRequest
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalInfo
import com.github.ajalt.mordant.terminal.TerminalInterface
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import io.github.oshai.kotlinlogging.KotlinLogging
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
import uesugi.core.*
import uesugi.core.message.history.HistoryRecord
import uesugi.core.message.history.HistoryTable
import uesugi.core.message.resource.ResourceRecord
import uesugi.core.message.resource.ResourceTable
import uesugi.core.route.MetaToolSetRegister
import uesugi.core.route.RouteCallEvent
import uesugi.toolkit.*
import java.io.InputStream
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.time.Duration
import kotlin.time.toJavaDuration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface Plugin {
    val name: String
    fun onLoad(context: PluginContext)
    fun onUnload() {}
}

interface CmdPlugin<Context, Arg : ArgParserHolder<Context>> : Plugin {
    val cmd: String

    fun Meta.parser(context: Context): Arg {
        val holder = getHolder(this@parser)
        holder.init(this@parser, context)
        val args = buildList {
            addAll(
                input!!.removePrefix("/")
                    .removePrefix(cmd)
                    .split(" ")
                    .filter { it.isNotBlank() }
                    .map { it.trim() }
            )
        }
        holder.main(args)
        return holder
    }

    @Suppress("UNCHECKED_CAST")
    private fun getHolder(meta: Meta): Arg {
        val superType = this@CmdPlugin::class.supertypes.first()
        val typeArg = superType.arguments[1].type
        val kClass = typeArg?.classifier as? KClass<*> ?: error("Not a class")
        val holder = kClass.createInstance()
        return holder as Arg
    }
}

object LoggerTerminalInterface : TerminalInterface {

    private val log = KotlinLogging.logger {}

    override fun info(
        ansiLevel: AnsiLevel?,
        hyperlinks: Boolean?,
        outputInteractive: Boolean?,
        inputInteractive: Boolean?
    ): TerminalInfo {
        return TerminalInfo(
            ansiLevel = AnsiLevel.NONE,
            outputInteractive = true,
            inputInteractive = true,
            supportsAnsiCursor = true,
            ansiHyperLinks = true
        )
    }

    override fun completePrintRequest(request: PrintRequest) {
        if (request.stderr) log.error { request.text }
        else log.info { request.text }
    }

    override fun readLineOrNull(hideInput: Boolean): String? {
        return null
    }
}

abstract class ArgParserHolder<Context> : CliktCommand() {

    companion object Empty : ArgParserHolder<Unit>() {
        override fun run() {
        }
    }

    init {
        context {
            terminal = Terminal(terminalInterface = LoggerTerminalInterface)
            exitProcess = {}
        }
    }

    open fun init(meta: Meta, context: Context) {}

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
    val vector: Vector

    val database: Database

    val scheduler: JobScheduler

    val llm: PromptExecutor
    val http: HttpClient

    val HttpClient.proxy: HttpClient

    val server: Server

    fun chain(handler: Handler)

    fun tool(toolset: PluginContext.() -> MetaToolSetCreator)

}

internal interface PluginContextBootstrap : PluginContext, AutoCloseable {
    fun open()
    fun ready()
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

interface Mem : AutoCloseable {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun set(key: String, value: String, expire: Duration, strategy: ExpireStrategy)
    suspend fun delete(key: String)
}

interface Kv : AutoCloseable {
    suspend fun get(key: String): String?
    suspend fun set(key: String, value: String)
    suspend fun set(key: String, value: String, expire: Duration, strategy: ExpireStrategy)
    suspend fun delete(key: String)
}

interface Blob : AutoCloseable {
    suspend fun get(path: Path): InputStream
    suspend fun set(path: Path, value: InputStream)
    suspend fun delete(path: Path)
}

interface Vector : AutoCloseable {
    suspend fun embedding(input: List<String>, images: List<ByteArray>): FloatArray
    suspend fun search(queryVector: FloatArray, topK: Int, filter: Map<String, String>? = null): List<SearchResult>
    suspend fun upsert(id: String, content: String, tag: String, vector: FloatArray)
    suspend fun delete(id: String)

    data class SearchResult(
        val id: String,
        val content: String,
        val score: Float
    )
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

interface Server {
    fun route(conf: Route.() -> Unit)
}

fun Meta.sendAgent(
    input: String,
    state: SendAgentStateDsl? = null,
) = sendAgent(input, SendAgentConf(), state)

fun Meta.sendAgent(
    input: String,
    conf: SendAgentConf = SendAgentConf(),
    state: SendAgentStateDsl? = null
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

typealias SendAgentStateDsl = SendAgentStateBuilder.() -> CoroutineScope

@Suppress("UNUSED")
class SendAgentStateBuilder(
    private val holder: MutableMap<String, Any>
) {

    fun sendBefore(builder: (sentences: List<String>) -> Unit) {
        holder["sendBefore"] = builder
    }

    fun sendAfter(builder: (sentences: List<String>) -> Unit) {
        holder["sendAfter"] = builder
    }

    fun sendReplay(builder: (sentence: String) -> Unit) {
        holder["sendReplay"] = builder
    }

    fun sendClosed(builder: () -> Unit) {
        holder["sendClosed"] = builder
    }

    fun sendFinally(builder: () -> Unit) {
        holder["sendFinally"] = builder
    }

    fun dispatchReject(builder: () -> Unit) {
        holder["dispatchReject"] = builder
    }

    fun dispatchFallback(builder: () -> Unit) {
        holder["dispatchFallback"] = builder
    }

    fun callStart(builder: () -> Unit) {
        holder["callStart"] = builder
    }

    fun callCompletion(builder: () -> Unit) {
        holder["callCompletion"] = builder
    }
}

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    dsl: SendAgentStateDsl? = null
) = sendAgent(botId, groupId, input, SendAgentConf(), dsl)

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    conf: SendAgentConf = SendAgentConf(),
    dsl: SendAgentStateDsl? = null
) {
    val holder = mutableMapOf<String, Any>()
    val fn = dsl?.let {
        val scope = SendAgentStateBuilder(holder).dsl()
        @Suppress("UNCHECKED_CAST")
        object : SendAgentState {
            override val scope = scope

            override fun sendBefore(sentences: List<String>) {
                (holder["sendBefore"] as? (List<String>) -> Unit)?.invoke(sentences)
            }

            override fun sendAfter(sentences: List<String>) {
                (holder["sendAfter"] as? (List<String>) -> Unit)?.invoke(sentences)
            }

            override fun sendReplay(sentence: String) {
                (holder["sendReplay"] as? (String) -> Unit)?.invoke(sentence)
            }

            override fun sendClosed() {
                (holder["sendClosed"] as? () -> Unit)?.invoke()
            }

            override fun sendFinally() {
                (holder["sendFinally"] as? () -> Unit)?.invoke()
            }

            override fun dispatchReject() {
                (holder["dispatchReject"] as? () -> Unit)?.invoke()
            }

            override fun dispatchFallback() {
                (holder["dispatchFallback"] as? () -> Unit)?.invoke()
            }

            override fun callStart() {
                (holder["callStart"] as? () -> Unit)?.invoke()
            }

            override fun callCompletion() {
                (holder["callCompletion"] as? () -> Unit)?.invoke()
            }
        }
    }
    sendAgent(botId, groupId, input, conf, fn)
}

private fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    conf: SendAgentConf = SendAgentConf(),
    state: SendAgentState? = null
) {
    val (chatPointRule, webSearch, toolSets, flag, echo) = conf
    if (state != null) {
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
    }

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
        filter: Map<String, String>?
    ): List<Vector.SearchResult> {
        return default.search(queryVector, topK, filter).map {
            Vector.SearchResult(it.id, it.content, it.score)
        }
    }

    override suspend fun upsert(id: String, content: String, tag: String, vector: FloatArray) {
        default.upsert(id, content, tag, vector)
    }

    override suspend fun delete(id: String) {
        default.delete(id)
    }

    override fun close() {
        default.close()
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