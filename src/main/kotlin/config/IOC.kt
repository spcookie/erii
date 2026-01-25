package uesugi.config

import io.ktor.server.application.*
import okio.Path.Companion.toPath
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.koinModule
import uesugi.core.emotion.EmotionJob
import uesugi.core.emotion.EmotionService
import uesugi.core.evolution.EvolutionJob
import uesugi.core.evolution.VocabularyService
import uesugi.core.flow.FlowGaugeManager
import uesugi.core.flow.FlowJob
import uesugi.core.history.HistoryService
import uesugi.core.memory.MemoryJob
import uesugi.core.memory.MemoryService
import uesugi.core.resource.ResourceService
import uesugi.core.volition.VolitionGaugeManager
import uesugi.core.volition.VolitionJob
import uesugi.toolkit.LocalStorage
import uesugi.toolkit.Storage
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
    }
}

fun Application.configBaseModule() = koinModule {
    single<Storage> {
        LocalStorage(
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
}

val serviceModule = module {
    singleOf(::VocabularyService)
    singleOf(::EmotionService)
    singleOf(::MemoryService)
    singleOf(::HistoryService)
    singleOf(::ResourceService)
    single { FlowGaugeManager() } onClose { it?.stopAll() }
    single { VolitionGaugeManager() } onClose { it?.stopAll() }
}

val jobModule = module {
    single { EmotionJob(get()) }
    single { MemoryJob(get()) }
    single { FlowJob(get()) }
    single { VolitionJob(get()) }
    single { EvolutionJob(get()) }
}

val gatewayModule = module {
    single { HttpClientFactory().createClient() }
    single { WebSearchClient(System.getenv("WEB_SEARCH_HOST")) }
}

val adapterModule = module {
    includes(jobModule)
}

val infrastructureModule = module {
    includes(gatewayModule)
    single { LLMFactory().promptExecutor() }
    single { WebPageMarkdownScraper() }
}