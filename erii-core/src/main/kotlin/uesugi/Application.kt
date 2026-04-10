package uesugi

import io.ktor.server.application.*
import uesugi.common.ConfigHolder
import uesugi.common.logger
import uesugi.config.ConfigHolderImpl
import uesugi.config.configureH2Console
import uesugi.core.bot.configureBotAgent
import uesugi.core.bot.configureConnectBots
import uesugi.server.*

internal val LOG = logger("uesugi")

fun main(args: Array<String>) {
    printBanner()
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    ConfigHolder.init(ConfigHolderImpl())
    SystemConfigHolder.init(this)

    configureFrameworks()
    configureMonitoring()
    configureSecurity()
    configureHTTP()
    configureRouting()

    configureBotAgent()
    configureConnectBots()
    configureH2Console()
}

fun printBanner() {
    val banner = ::main.javaClass.classLoader.getResourceAsStream("banner.txt")?.bufferedReader()?.readText()
    if (banner != null) {
        println(banner.replace("{version}", Version.CURRENT))
    }
}