package uesugi.config

import io.ktor.server.application.*
import okio.Path.Companion.toPath
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.onClose
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
import javax.sql.DataSource


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

val jobModule = module(createdAtStart = true) {
    single { EmotionJob(get()).apply { openTimingTriggerSignal() } }
    single { MemoryJob(get()).apply { openTimingTriggerSignal() } }
    single { FlowJob(get()).apply { openTimingTriggerSignal() } }
    single { VolitionJob(get()).apply { openTimingTriggerSignal() } }
    single { EvolutionJob(get()).apply { openTimingTriggerSignal() } }
}

val gatewayModule = module {
    single { HttpClientFactory().createClient() }
}

val adapterModule = module {
    includes(jobModule)
}

val infrastructureModule = module {
    includes(gatewayModule)
    single { LLMFactory().promptExecutor() }
}