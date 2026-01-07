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
import uesugi.core.emotion.EmotionalTendencies
import uesugi.core.evolution.EvolutionJob
import uesugi.core.evolution.LearnedVocabTable
import uesugi.core.evolution.VocabularyService
import uesugi.core.flow.FlowGauge
import uesugi.core.flow.FlowHandler
import uesugi.core.history.HistoryService
import uesugi.core.history.HistoryTable
import uesugi.core.memory.*
import uesugi.core.volition.VolitionGauge
import uesugi.core.volition.VolitionHandler


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
            }
        }
    }
    single { JobRunrConfig() }
}

val serviceModule = module {
    singleOf(::VocabularyService)
    singleOf(::EmotionService)
    singleOf(::MemoryService)
    singleOf(::HistoryService)
    single { FlowGauge(EmotionalTendencies.BASE_LINE, 30 * 1000L) }
    single { VolitionGauge(EmotionalTendencies.BASE_LINE) }
    single { FlowHandler(get()).apply { start() } } onClose { it?.close() }
    single { VolitionHandler(get()).apply { start() } } onClose { it?.close() }
}

val jobModule = module(createdAtStart = true) {
    single { EmotionJob().apply { openTimingTriggerSignal() } }
    single { MemoryJob().apply { openTimingTriggerSignal() } }
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

fun installIoc() {
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