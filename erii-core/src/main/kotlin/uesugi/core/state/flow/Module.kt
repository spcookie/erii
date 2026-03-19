package uesugi.core.state.flow

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.koin.dsl.onClose

val flowModule = module {
    singleOf(::FlowAgent)
    singleOf(::FlowRepository)
    singleOf(::FlowJob)
    single { FlowGaugeManager() } onClose { it?.stopAll() }
}