package uesugi.core.state.emotion

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val emotionModule = module {
    singleOf(::EmotionRepository)
    singleOf(::EmotionService)
    singleOf(::EmotionJob)
}