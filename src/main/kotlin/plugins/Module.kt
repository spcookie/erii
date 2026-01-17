package uesugi.plugins

import org.koin.dsl.module
import org.koin.dsl.onClose
import plugins.steamwatcher.SteamWatcher
import uesugi.plugins.chatgpt.ChatQA
import uesugi.plugins.lolisuki.Lolisuki
import uesugi.plugins.nano.ImageCreator

fun pluginModule() = module(createdAtStart = true) {
    single { SteamWatcher().apply { onLoad() } } onClose { it?.onUnload() }
    single { Lolisuki().apply { onLoad() } } onClose { it?.onUnload() }
    single { ChatQA().apply { onLoad() } } onClose { it?.onUnload() }
    single { ImageCreator().apply { onLoad() } } onClose { it?.onUnload() }
}