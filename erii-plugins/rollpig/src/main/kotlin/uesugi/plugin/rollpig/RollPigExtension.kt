package uesugi.plugin.rollpig

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.pf4j.Extension
import uesugi.plugin.rollpig.service.RollPigService
import uesugi.plugin.rollpig.store.PigData
import uesugi.plugin.rollpig.store.RollPigStore
import uesugi.spi.CmdExtension
import uesugi.spi.PluginContext
import java.io.InputStream

@Extension
class RollPigExtension : CmdExtension<RollPigContext, RollPigArgParser, RollPig> {

    override val cmd: String = "rollpig"

    override val alias: List<String>
        get() = listOf("rp", "今日小猪", "抽小猪")

    private val log = KotlinLogging.logger {}

    private lateinit var context: PluginContext
    private lateinit var store: RollPigStore
    private lateinit var service: RollPigService
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val DAILY_RESET_JOB_ID = "rollpig-daily-reset"
        private const val DAILY_RESET_CRON = "0 0 * * *"
    }

    override fun onLoad(context: PluginContext) {
        this.context = context

        store = RollPigStore(context.kv)

        loadPigData(context)

        service = RollPigService(store)

        registerCommandHandler()
        startDailyResetTask()

        log.info { "RollPigExtension loaded" }
    }

    private fun loadPigData(ctx: PluginContext) {
        try {
            val pigJsonStream: InputStream = runBlocking {
                ctx.config.readResource("pig.json")
            }
            val pigList = Json.decodeFromString<List<PigData>>(pigJsonStream.bufferedReader().use { it.readText() })
            store.loadPigList(pigList)
            log.info { "Loaded ${pigList.size} pigs" }
        } catch (e: Exception) {
            log.error(e) { "Failed to load pig data" }
        }
    }

    private fun registerCommandHandler() {
        context.chain { meta ->
            RollPigCommandHandler(
                store = store,
                service = service,
                scope = scope
            ).handleWithError(meta) { ctx ->
                meta.parser(ctx)
            }
        }
    }

    override fun onUnload() {
        context.scheduler.cancel(DAILY_RESET_JOB_ID)
        scope.cancel()
        log.info { "RollPigExtension unloaded" }
    }

    private fun startDailyResetTask() {
        context.scheduler.scheduleRecurrently(DAILY_RESET_JOB_ID, DAILY_RESET_CRON) {
            log.info { "Running rollpig daily reset" }
            try {
                runBlocking {
                    store.clearTodayCache()
                }
            } catch (e: Exception) {
                log.error(e) { "Error in rollpig daily reset" }
            }
        }
        log.info { "RollPig daily reset scheduled with cron: $DAILY_RESET_CRON" }
    }
}
