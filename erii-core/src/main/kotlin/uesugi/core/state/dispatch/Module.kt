package uesugi.core.state.dispatch

import org.koin.dsl.module
import org.koin.dsl.onClose
import uesugi.common.toolkit.ConfigHolder
import uesugi.core.state.emotion.EmotionJob
import uesugi.core.state.evolution.EvolutionJob
import uesugi.core.state.flow.FlowJob
import uesugi.core.state.meme.MemeAnalyzeProcessor
import uesugi.core.state.meme.MemeCollectProcessor
import uesugi.core.state.memory.MemoryJob
import uesugi.core.state.summary.SummaryJob
import uesugi.core.state.volition.VolitionJob

val stateDispatchModule = module {
    single {
        val tuning = ConfigHolder.getStateTuning().dispatch
        val processors = listOf<StateWorkProcessor>(
            get<EmotionJob>(),
            get<FlowJob>(),
            get<VolitionJob>(),
            get<MemoryJob>(),
            get<SummaryJob>(),
            get<MemeCollectProcessor>(),
            get<MemeAnalyzeProcessor>(),
            get<EvolutionJob>()
        )
        StateWorkCoordinator(
            processors = processors,
            policies = tuning.profile.policies(),
            maxConcurrency = tuning.maxConcurrency
        )
    } onClose { it?.close() }
    single { StateDispatchJob(get(), get()) }
}
