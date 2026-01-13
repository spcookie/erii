package uesugi.config

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.dsl.onClose
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
import uesugi.core.volition.VolitionGaugeManager
import uesugi.core.volition.VolitionJob
import uesugi.core.volition.VolitionStateTable
import javax.sql.DataSource


val configModule = module(createdAtStart = true) {
    single {
        val connectionFactoryConfig = ConnectionFactoryConfig()
        transaction {
            SchemaUtils.create(
                HistoryTable,
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
        }
        connectionFactoryConfig.dataSource
    } bind DataSource::class
    single { JobRunrConfig().apply { start(get()) } }
}

val serviceModule = module {
    singleOf(::VocabularyService)
    singleOf(::EmotionService)
    singleOf(::MemoryService)
    singleOf(::HistoryService)
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