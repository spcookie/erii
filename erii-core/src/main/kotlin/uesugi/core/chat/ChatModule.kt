package uesugi.core.chat

import org.koin.dsl.module

val chatModule = module {
    single { ChatBridge(get()) }
}
