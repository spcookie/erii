package uesugi.core.message

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import uesugi.core.message.history.HistoryService
import uesugi.core.message.pipeline.MessagePipeline
import uesugi.core.message.resource.ResourceService
import uesugi.core.message.resource.ThumbnailService

val messageModule = module {
    singleOf(::HistoryService)
    singleOf(::ResourceService)
    singleOf(::MessagePipeline)
    single { ThumbnailService(get()) }
}