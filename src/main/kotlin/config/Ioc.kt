package uesugi.config

import io.ktor.server.application.*
import io.ktor.server.plugins.di.*
import okio.Path.Companion.toPath
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.migration.jdbc.MigrationUtils
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.ktor.plugin.koinModule
import uesugi.core.emotion.EmotionJob
import uesugi.core.emotion.EmotionService
import uesugi.core.emotion.EmotionTable
import uesugi.core.evolution.EvolutionJob
import uesugi.core.evolution.LearnedVocabTable
import uesugi.core.evolution.VocabularyService
import uesugi.core.flow.FlowGaugeManager
import uesugi.core.flow.FlowJob
import uesugi.core.flow.FlowStateTable
import uesugi.core.history.HistoryService
import uesugi.core.history.HistoryTable
import uesugi.core.memory.*
import uesugi.core.resource.ResourceService
import uesugi.core.resource.ResourceTable
import uesugi.core.volition.VolitionGaugeManager
import uesugi.core.volition.VolitionJob
import uesugi.core.volition.VolitionStateTable
import uesugi.toolkit.LocalStorage
import uesugi.toolkit.Storage
import javax.sql.DataSource


fun Application.configModule() = koinModule {
    single {
        Database.connect(
            get(),
            {},
            DatabaseConfig {
                useNestedTransactions = true
            }
        )
        TransactionManager.defaultDatabase = Database.Companion.connect(
            get(),
            {},
            DatabaseConfig {
                useNestedTransactions = true
            }
        )
    }
    single { JobRunrConfig(get()) }
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
    single { EmotionJob().apply { openTimingTriggerSignal() } }
    single { MemoryJob().apply { openTimingTriggerSignal() } }
    single { FlowJob().apply { openTimingTriggerSignal() } }
    single { VolitionJob().apply { openTimingTriggerSignal() } }
    single { EvolutionJob(get()).apply { openTimingTriggerSignal() } }
}

val adapterModule = module {
    includes(jobModule)
}

val infrastructureModule = module {
    single {
        LLMFactory().promptExecutor()
    }
    single {
        HttpClientFactory().createClient()
    }
}

fun Application.configBaseModule() {
    dependencies {
        provide<Storage> {
            LocalStorage(
                baseDir = "./store/object".toPath()
            )
        }
        provide<DataSource> {
            val connectionFactoryConfig = ConnectionFactoryConfig()
            transaction {
                SchemaUtils.create(
                    HistoryTable,
                    ResourceTable,
                    EmotionTable,
                    FactsTable,
                    TodoTable,
                    UserProfileTable,
                    SummaryTable,
                    MemoryStateTable,
                    LearnedVocabTable,
                    FlowStateTable,
                    VolitionStateTable
                )
                val migration = MigrationUtils.statementsRequiredForDatabaseMigration(
                    HistoryTable
                )
                execInBatch(migration)
            }
            connectionFactoryConfig.dataSource
        }
    }
}