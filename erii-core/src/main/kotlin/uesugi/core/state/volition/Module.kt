package uesugi.core.state.volition

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.onClose

val volitionModule = module {
    singleOf(::VolitionAgent)
    singleOf(::VolitionRepository)
    singleOf(::VolitionJob)
    single { VolitionGaugeManager() } onClose { it?.stopAll() }
}