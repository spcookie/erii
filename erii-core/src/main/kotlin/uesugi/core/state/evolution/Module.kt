package uesugi.core.state.evolution

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val evolutionModule = module {
    singleOf(::EvolutionRepository)
    singleOf(::EvolutionService)
    singleOf(::ExtractionAgent)
    singleOf(::EvolutionJob)
}