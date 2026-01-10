package uesugi.config

import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.koin.core.context.GlobalContext.loadKoinModules
import org.koin.core.context.GlobalContext.startKoin
import org.koin.core.logger.Level
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.onClose
import org.koin.environmentProperties
import org.koin.logger.SLF4JLogger
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


val configModule = module(createdAtStart = true) {
    single {
        ConnectionFactoryConfig().apply {
            transaction {
                SchemaUtils.create(HistoryTable)
                SchemaUtils.create(EmotionTable)
                SchemaUtils.create(FactsTable)
                SchemaUtils.create(TodoTable)
                SchemaUtils.create(UserProfileTable)
                SchemaUtils.create(SummaryTable)
                SchemaUtils.create(MemoryStateTable)
                SchemaUtils.create(LearnedVocabTable)
                SchemaUtils.create(FlowStateTable)
                SchemaUtils.create(VolitionStateTable)
            }
        }
    }
    single { JobRunrConfig().apply { start() } }
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
        LLMFactory.promptExecutor()
    }
}

fun installIOC() {
    startKoin {
        logger(SLF4JLogger(Level.INFO))
        environmentProperties()
        modules(configModule)
        createEagerInstances()
    }
    loadKoinModules(
        listOf(adapterModule, infrastructureModule, serviceModule),
        true
    )
}