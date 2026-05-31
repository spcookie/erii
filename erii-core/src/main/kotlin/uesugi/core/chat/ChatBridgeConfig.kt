package uesugi.core.chat

import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.get
import uesugi.common.toolkit.logger

fun Application.configureChatBridge() {
    monitor.subscribe(ApplicationStarted) { application ->
        runBlocking {
            try {
                application.get<ChatBridge>().start()
            } catch (e: Exception) {
                LOG.error("ChatBridge failed to start: ${e.message}", e)
            }
        }
    }

    monitor.subscribe(ApplicationStopped) { application ->
        runBlocking {
            application.get<ChatBridge>().stop()
        }
    }
}

private val LOG = logger("ChatBridge")
