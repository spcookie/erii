package uesugi.core.state.meme

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val memeModule = module {
    singleOf(::MemeAgent)
    singleOf(::MemeRepository)
    singleOf(::MemeService)
    singleOf(::MemeJob)
    singleOf(::MemoVectorStore)
}