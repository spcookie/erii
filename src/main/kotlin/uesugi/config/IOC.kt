package uesugi.config

import io.ktor.server.application.*
import okio.Path.Companion.toPath
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.koinModule
import uesugi.core.message.history.HistoryService
import uesugi.core.message.resource.ResourceService
import uesugi.core.state.emotion.EmotionJob
import uesugi.core.state.emotion.EmotionService
import uesugi.core.state.evolution.EvolutionJob
import uesugi.core.state.evolution.VocabularyService
import uesugi.core.state.flow.FlowGaugeManager
import uesugi.core.state.flow.FlowJob
import uesugi.core.state.meme.*
import uesugi.core.state.memory.MemoryJob
import uesugi.core.state.memory.MemoryService
import uesugi.core.state.volition.VolitionGaugeManager
import uesugi.core.state.volition.VolitionJob
import uesugi.plugins.system.status.WebScreenshotTaker
import uesugi.toolkit.LocalObjectStorage
import uesugi.toolkit.ObjectStorage
import uesugi.toolkit.WebPageMarkdownScraper
import uesugi.toolkit.WebSearchClient
import javax.sql.DataSource

fun Application.warmUp() {
    monitor.subscribe(ApplicationStarted) {
        it.get<EmotionJob>().apply { openTimingTriggerSignal() }
        it.get<MemoryJob>().apply { openTimingTriggerSignal() }
        it.get<VolitionJob>().apply { openTimingTriggerSignal() }
        it.get<EvolutionJob>().apply { openTimingTriggerSignal() }
        it.get<FlowJob>().apply { openTimingTriggerSignal() }
        it.get<MemeCollectJob>().apply { openTimingTriggerSignal() }
        it.get<MemeExtractJob>().apply { openTimingTriggerSignal() }
    }
}

fun Application.configBaseModule() = koinModule {
    single<ObjectStorage> {
        LocalObjectStorage(
            baseDir = "./store/object".toPath()
        )
    }
    single<DataSource> {
        ConnectionFactoryConfig().getDataSource()
    }
    single(createdAtStart = true) {
        val database = Database.connect(
            get(),
            {},
            DatabaseConfig {
                useNestedTransactions = true
            }
        )
        TransactionManager.defaultDatabase = database
        database
    }
    single(createdAtStart = true) { JobRunrConfig(get()).run { start() } }

    single(named("port")) {
        environment.config
            .property("ktor.deployment.port")
            .getString()
            .toInt()
    }

}

val serviceModule = module {
    singleOf(::VocabularyService)
    singleOf(::EmotionService)
    singleOf(::MemoryService)
    singleOf(::HistoryService)
    singleOf(::ResourceService)
    single { MemoVectorStoreFactory() }
    single { MemoService(get()) }
    single { MemeAgent() }
    single { FlowGaugeManager() } onClose { it?.stopAll() }
    single { VolitionGaugeManager() } onClose { it?.stopAll() }
}

val jobModule = module {
    singleOf(::EmotionJob)
    singleOf(::MemoryJob)
    singleOf(::FlowJob)
    singleOf(::VolitionJob)
    singleOf(::EvolutionJob)
    singleOf(::MemeCollectJob)
    singleOf(::MemeExtractJob)
}

val gatewayModule = module {
    val noProxyClient = HttpClientFactory().createClient()
    val proxyClient = HttpClientFactory().createProxyClient()
    single { noProxyClient }
    single(named(HttpClientFactory.Type.NO_PROXY)) { noProxyClient }
    single(named(HttpClientFactory.Type.PROXY)) { proxyClient }

    single { WebSearchClient(System.getenv("WEB_SEARCH_HOST")) }
}

val adapterModule = module {
    includes(jobModule)
}

val infrastructureModule = module {
    includes(gatewayModule)
    single { LLMFactory().promptExecutor() }
    single { WebPageMarkdownScraper() }
    single { WebScreenshotTaker() }
}