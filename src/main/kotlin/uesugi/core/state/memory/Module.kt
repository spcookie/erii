package uesugi.core.state.memory

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val memoryModule = module {
    singleOf(::MemoryAgent)
    singleOf(::MemoryRepository)
    singleOf(::MemoryService)
    singleOf(::MemoryJob)
    singleOf(::FactVectorStore)
}
