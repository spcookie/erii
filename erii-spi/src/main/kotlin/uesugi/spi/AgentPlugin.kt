package uesugi.spi


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
import com.typesafe.config.Config
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.server.routing.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okio.Path
import org.jetbrains.exposed.v1.jdbc.Query
import org.jobrunr.scheduling.JobScheduler
import uesugi.common.*
import java.io.InputStream
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

sealed interface AgentPlugin {
    val name: String
    fun onLoad(context: PluginContext)
    fun onUnload() {}
}

interface CmdPlugin<Context, Arg : ArgParserHolder<Context>> : AgentPlugin {
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

interface RoutePlugin : AgentPlugin {
    val matcher: Pair<String, String>
}

interface PassivePlugin : AgentPlugin, ClassNameMixin

interface ClassNameMixin : AgentPlugin {
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

    val config: PluginConfig

    val database: Database

    val scheduler: JobScheduler

    val llm: PromptExecutor
    val http: HttpClient

    val HttpClient.proxy: HttpClient

    val server: Server

    fun chain(handler: Handler)

    fun tool(toolset: PluginContext.() -> MetaToolSetCreator)

}

interface PluginContextBootstrap : PluginContext, AutoCloseable {
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
    suspend fun search(queryVector: FloatArray, topK: Int, filter: List<String>? = null): List<SearchResult>
    suspend fun upsert(id: String, content: String, tag: String, vector: FloatArray)
    suspend fun delete(id: String)
    suspend fun deleteAll()

    data class SearchResult(
        val id: String,
        val content: String,
        val tag: String,
        val score: Float
    )
}

/**
 * 插件配置接口，用于读取插件配置文件
 */
interface PluginConfig {
    suspend fun read(path: String): InputStream

    /**
     * 获取插件的 Typesafe Config 对象
     * 支持从以下位置读取配置：
     * 1. -Dplugin.{PluginName}.config 参数指定的文件
     * 2. -Dconfig.plugin.dir 参数指定的目录下的 {PluginName}.conf 文件
     * 3. classpath 中的 /{PluginName}.conf 文件
     */
    fun getPluginConfig(): Config
}

interface Meta {
    val botId: String
    val groupId: String
    val roledBot: IBotManage.RoledBot
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
) = sendAgent(input, EmptyConfig, state)

fun Meta.sendAgent(
    input: String,
    conf: SendAgentConfig = EmptyConfig,
    state: SendAgentStateDsl? = null
) = sendAgent(botId, groupId, input, conf, state)

interface SendAgentState {
    val scope: CoroutineScope
    suspend fun callToolStart(
        toolName: String,
        toolArgs: JsonObject
    ) {

    }

    suspend fun callToolCompletion(
        toolName: String,
        toolArgs: JsonObject,
        toolResult: JsonElement?,
        toolError: String?
    ) {
    }

    suspend fun runStart() {

    }

    suspend fun runCompletion(error: Throwable?) {

    }

    suspend fun callReject() {

    }

    suspend fun callFallback() {

    }

    suspend fun callStart() {

    }

    suspend fun callCompletion(error: Throwable?) {

    }
}

@OptIn(ExperimentalUuidApi::class)
data class SendAgentConf(
    val webSearch: Boolean = false,
    val toolSetBuilder: ((ChatToolSet) -> List<ToolSet>)? = null,
    val feature: ProactiveSpeakFeature = PSFeature.NONE,
    val echo: String = Uuid.random().toHexString(),
)

interface SendAgentConfig {

    operator fun SendAgentConfig.plus(config: SendAgentConfig): SendAgentConfig {
        if (config == EmptyConfig) return this
        return config.fold(this) { acc, elem ->
            val removed = acc.minusKey(elem.key)
            if (removed == EmptyConfig) config
            else CombinedConfig(removed, elem)
        }
    }

    fun <R> fold(initial: R, op: (R, Elem) -> R): R

    fun minusKey(key: Key<*>): SendAgentConfig

    operator fun <K : Elem> get(key: Key<K>): K?

    interface Key<K : Elem>

    interface Elem : SendAgentConfig {

        val key: Key<*>

        override fun minusKey(key: Key<*>): SendAgentConfig {
            return if (key == this.key) EmptyConfig else this
        }

        override fun <R> fold(initial: R, op: (R, Elem) -> R): R {
            return op(initial, this)
        }

        @Suppress("UNCHECKED_CAST")
        override fun <K : Elem> get(key: Key<K>): K? {
            return if (key == this.key) this as K else null
        }
    }

}

class CombinedConfig(
    val left: SendAgentConfig,
    val elem: SendAgentConfig.Elem
) : SendAgentConfig {

    override fun <R> fold(initial: R, op: (R, SendAgentConfig.Elem) -> R): R {
        val newInitial = left.fold(initial, op)
        return op(newInitial, elem)
    }

    override fun minusKey(key: SendAgentConfig.Key<*>): SendAgentConfig {
        if (key == elem.key) return left

        return when (val newLeft = left.minusKey(key)) {
            left -> this
            EmptyConfig -> elem
            else -> CombinedConfig(newLeft, elem)
        }
    }

    override fun <K : SendAgentConfig.Elem> get(key: SendAgentConfig.Key<K>): K? {
        elem[key]?.let { return it }
        return left[key]
    }
}

object EmptyConfig : SendAgentConfig {
    override fun <R> fold(initial: R, op: (R, SendAgentConfig.Elem) -> R): R {
        return initial
    }

    override fun minusKey(key: SendAgentConfig.Key<*>): SendAgentConfig {
        return this
    }

    override fun <K : SendAgentConfig.Elem> get(key: SendAgentConfig.Key<K>): K? {
        return null
    }

}

enum class WebSearch : SendAgentConfig.Elem {

    ENABLE, DISABLE;

    companion object Key : SendAgentConfig.Key<WebSearch>

    override val key: SendAgentConfig.Key<*>
        get() = WebSearch

}

class ToolSetBuilder(
    val value: ((ChatToolSet) -> List<ToolSet>)
) : SendAgentConfig.Elem {


    companion object Key : SendAgentConfig.Key<ToolSetBuilder>

    override val key: SendAgentConfig.Key<*>
        get() = ToolSetBuilder

}

class Feature(val value: ProactiveSpeakFeature) : SendAgentConfig.Elem {
    companion object Key : SendAgentConfig.Key<Feature>

    override val key: SendAgentConfig.Key<*>
        get() = Feature
}

typealias SendAgentStateDsl = SendAgentStateBuilder.() -> CoroutineScope

@Suppress("UNUSED")
class SendAgentStateBuilder(
    private val holder: MutableMap<String, Any>
) {

    fun callToolStart(builder: suspend (String, JsonObject) -> Unit) {
        holder["callToolStart"] = builder
    }

    fun callToolCompletion(
        builder: suspend (
            String,
            JsonObject,
            JsonElement?,
            String?
        ) -> Unit
    ) {
        holder["callToolCompletion"] = builder
    }

    fun runStart(builder: suspend () -> Unit) {
        holder["runStart"] = builder
    }

    fun runCompletion(builder: suspend (Throwable?) -> Unit) {
        holder["runCompletion"] = builder
    }

    fun callReject(builder: suspend () -> Unit) {
        holder["callReject"] = builder
    }

    fun callFallback(builder: suspend () -> Unit) {
        holder["callFallback"] = builder
    }

    fun callStart(builder: suspend () -> Unit) {
        holder["callStart"] = builder
    }

    fun callCompletion(builder: suspend (Throwable?) -> Unit) {
        holder["callCompletion"] = builder
    }
}

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    dsl: SendAgentStateDsl? = null
) = sendAgent(botId, groupId, input, EmptyConfig, dsl)

fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    config: SendAgentConfig = EmptyConfig,
    dsl: SendAgentStateDsl? = null
) {
    val holder = mutableMapOf<String, Any>()
    val fn = dsl?.let {
        val scope = SendAgentStateBuilder(holder).dsl()
        @Suppress("UNCHECKED_CAST")
        object : SendAgentState {
            override val scope = scope

            override suspend fun callToolStart(toolName: String, toolArgs: JsonObject) {
                (holder["callToolStart"] as? suspend (String, JsonObject) -> Unit)?.invoke(toolName, toolArgs)
            }

            override suspend fun callToolCompletion(
                toolName: String,
                toolArgs: JsonObject,
                toolResult: JsonElement?,
                toolError: String?
            ) {
                (holder["callToolCompletion"] as? suspend (
                    String,
                    JsonObject,
                    JsonElement?,
                    String?
                ) -> Unit)?.invoke(
                    toolName,
                    toolArgs,
                    toolResult,
                    toolError
                )
            }

            override suspend fun runStart() {
                (holder["runStart"] as? suspend () -> Unit)?.invoke()
            }

            override suspend fun runCompletion(error: Throwable?) {
                (holder["runCompletion"] as? suspend (Throwable?) -> Unit)?.invoke(error)
            }

            override suspend fun callReject() {
                (holder["callReject"] as? suspend () -> Unit)?.invoke()
            }

            override suspend fun callFallback() {
                (holder["callFallback"] as? suspend () -> Unit)?.invoke()
            }

            override suspend fun callStart() {
                (holder["callStart"] as? suspend () -> Unit)?.invoke()
            }

            override suspend fun callCompletion(error: Throwable?) {
                (holder["callCompletion"] as? suspend (Throwable?) -> Unit)?.invoke(error)
            }
        }
    }
    val conf = SendAgentConf(
        webSearch = config[WebSearch]?.let { it == WebSearch.ENABLE } ?: false,
        toolSetBuilder = config[ToolSetBuilder]?.value,
        feature = config[Feature]?.value ?: PSFeature.NONE,
    )
    sendAgent(botId, groupId, input, conf, fn)
}


private fun sendAgent(
    botId: String,
    groupId: String,
    input: String,
    conf: SendAgentConf = SendAgentConf(),
    state: SendAgentState? = null
) {
    val (webSearch, toolSets, flag, echo) = conf

    if (state != null) {
        val job = EventBus.subscribeAsync<AgentToolCallEvent>(state.scope) { event ->
            val toolCallEvent = event.takeIf { event.botId == botId && event.groupId == groupId && event.echo == echo }
            if (toolCallEvent != null) {
                when (toolCallEvent) {
                    is AgentToolCallStartEvent -> state.callToolStart(
                        toolCallEvent.toolName,
                        toolCallEvent.toolArgs
                    )

                    is AgentToolCallCompleteEvent -> state.callToolCompletion(
                        toolCallEvent.toolName,
                        toolCallEvent.toolArgs,
                        toolCallEvent.toolResult,
                        toolCallEvent.toolError
                    )
                }
            }
        }

        EventBus.subscribeOnceSync<AgentCallStartEvent> { event ->
            val e = event.takeIf { event.botId == botId && event.groupId == groupId && event.echo == echo }
            if (e != null) {
                runBlocking(state.scope.coroutineContext) {
                    state.callStart()
                }
            }
        }

        val runStart = EventBus.subscribeSync<AgentRunStartEvent> { event ->
            val e = event.takeIf { event.botId == botId && event.groupId == groupId && event.echo == echo }
            if (e != null) {
                runBlocking(state.scope.coroutineContext) {
                    state.runStart()
                }
            }
        }

        val runCompletion = EventBus.subscribeSync<AgentRunCompleteEvent> { event ->
            val e = event.takeIf { event.botId == botId && event.groupId == groupId && event.echo == echo }
            if (e != null) {
                runBlocking(state.scope.coroutineContext) {
                    state.runCompletion(event.throwable)
                }
            }
        }

        fun AgentDispatchEvent.call(block: () -> Unit) {
            val e = this.takeIf { this.botId == botId && this.groupId == groupId && this.echo == echo }
            if (e != null) {
                runCatching { block() }
                EventBus.unsubscribeAsync(job)
                EventBus.unsubscribeSync(runStart)
                EventBus.unsubscribeSync(runCompletion)
            }
        }

        EventBus.subscribeOnceSync<AgentCallRejectEvent> { event ->
            event.call { runBlocking(state.scope.coroutineContext) { state.callReject() } }
        }

        EventBus.subscribeOnceSync<AgentCallFallbackEvent> { event ->
            event.call { runBlocking(state.scope.coroutineContext) { state.callFallback() } }
        }

        EventBus.subscribeOnceSync<AgentCallCompleteEvent> { event ->
            event.call { runBlocking(state.scope.coroutineContext) { state.callCompletion(event.throwable) } }
        }
    }

    EventBus.postAsync(
        ProactiveSpeakEvent(
            botId = botId,
            _groupId = groupId,
            interruptionMode = InterruptionMode.Interrupt,
            input = input,
            webSearch = webSearch,
            toolSetBuilder = toolSets,
            feature = flag,
            echo = echo
        )
    )

}
