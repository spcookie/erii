package uesugi

import ch.qos.logback.classic.LoggerContext
import io.github.oshai.kotlinlogging.KotlinLoggingConfiguration
import io.ktor.server.application.*
import io.ktor.server.netty.*
import org.slf4j.LoggerFactory
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

internal val LOG by lazy { logger("uesugi") }

fun main(args: Array<String>) {
    printBanner()
    configureLogging()
    EngineMain.main(args)
}

fun Application.module() {
    configurePrintCliStartupInfo()

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

fun configureLogging() {
    val loggerContext = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return
    val rootLogger = loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)

    if (System.getenv("ERII_START_MODE") == "CLI") {
        KotlinLoggingConfiguration.logStartupMessage = false
        rootLogger.detachAppender("STDOUT")
    } else {
        rootLogger.detachAppender("INFO_FILE")
    }
}

fun Application.configurePrintCliStartupInfo() {
    if (System.getenv("ERII_START_MODE") == "CLI") {
        environment.monitor.subscribe(ApplicationStarted) {
            if (System.getenv("ERII_START_MODE") != "CLI") return@subscribe

            val port = environment.config.property("ktor.deployment.port").getString().toInt()
            val username = environment.config.property("security.username").getString()
            val password = environment.config.property("security.password").getString()

            if (username == "eriix" && password == "@Aa123!") {
                println("[WARN] Using default credentials: username=$username, password=$password")
                println("[WARN] Please change default credentials via env vars ERII_SERVER_USERNAME / ERII_SERVER_PASSWORD")
            }

            println("Erii started successfully")
            println("Erii Status -> http://localhost:${port}/bots")

            val h2ConsoleEnabled = System.getProperty("h2.console.enabled", "false").toBoolean()
            if (h2ConsoleEnabled) {
                val h2Port = System.getProperty("h2.console.port", "8082")
                println("H2 Console -> http://localhost:${h2Port}")
            }

            val jobrunrDashboardEnabled = System.getProperty("jobrunr.dashboard.enabled", "false").toBoolean()
            if (jobrunrDashboardEnabled) {
                val jobrunrPort = System.getProperty("jobrunr.dashboard.port", "8000")
                println("JobRunr Dashboard -> http://localhost:${jobrunrPort}")
            }
        }
    }
}

fun printBanner() {
    val banner = ::main.javaClass.classLoader.getResourceAsStream("banner.txt")?.bufferedReader()?.readText()
    if (banner != null) {
        println(banner.replace("{version}", Version.CURRENT))
    }
}