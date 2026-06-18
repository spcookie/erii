package uesugi.core.state.summary

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val summaryModule = module {
    singleOf(::SummaryAgent)
    singleOf(::SummaryRepository)
    singleOf(::SummaryService)
    singleOf(::SummaryJob)
}
