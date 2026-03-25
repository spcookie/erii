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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okio.Path
import org.jetbrains.exposed.v1.jdbc.Query
import org.jobrunr.scheduling.JobScheduler
import org.pf4j.ExtensionPoint
import org.pf4j.Plugin
import org.pf4j.PluginWrapper
import uesugi.common.*
import java.io.InputStream
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid


abstract class AgentPlugin : Plugin() {

    companion object {
        @JvmStatic
        var context: PluginWrapper? = null
    }

    fun setWrapper(wrapper: PluginWrapper) {
        super.wrapper = wrapper
        context = wrapper
    }

}

interface AgentExtension : ExtensionPoint {
    val name: String

    fun onLoad(context: PluginContext)

    fun onUnload() {}

}

interface CmdExtension<Context, Arg : ArgParserHolder<Context>> : AgentExtension {
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
        val superType = this@CmdExtension::class.supertypes.first()
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

interface RouteExtension : AgentExtension {
    val matcher: Pair<String, String>
}

interface PassiveExtension : AgentExtension, PluginIdNameMixin

interface PluginIdNameMixin : AgentExtension {

    override val name: String
        get() = (AgentPlugin.context?.let { it.pluginId + "_" } ?: "") + this::class.simpleName!!

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
    suspend fun readResource(path: String): InputStream

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
) {
    for (sender in ServiceLoader.load(AgentSender::class.java)) {
        sender.sendAgent(botId, groupId, input, conf, state)
    }
}


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


interface AgentSender {

    fun sendAgent(
        botId: String,
        groupId: String,
        input: String,
        dsl: SendAgentStateDsl? = null
    )

    fun sendAgent(
        botId: String,
        groupId: String,
        input: String,
        config: SendAgentConfig = EmptyConfig,
        dsl: SendAgentStateDsl? = null
    )

}