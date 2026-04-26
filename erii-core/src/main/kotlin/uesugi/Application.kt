package uesugi

import io.ktor.server.application.*
import uesugi.cli.configureIpc
import uesugi.common.toolkit.BrowserScraperHolder
import uesugi.common.toolkit.ConfigHolder
import uesugi.common.toolkit.logger
import uesugi.config.ConfigHolderImpl
import uesugi.config.configureH2Console
import uesugi.core.bot.configureBotAgent
import uesugi.core.bot.configureConnectBots
import uesugi.core.component.browser.BrowserScraperImpl
import uesugi.server.*

internal val LOG = logger("uesugi")

fun main(args: Array<String>) {
    printBanner()
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    ConfigHolder.init(ConfigHolderImpl())
    SystemConfigHolder.init(this)

    val browserScraperImpl = BrowserScraperImpl()
    BrowserScraperHolder.init(browserScraperImpl)
    this.monitor.unsubscribe(ApplicationStopped) {
        browserScraperImpl.close()
    }

    configureFrameworks()
    configureMonitoring()
    configureSecurity()
    configureHTTP()
    configureRouting()

    configureIpc()

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