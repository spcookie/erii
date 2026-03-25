package uesugi.toolkit

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Playwright
import uesugi.common.ConfigHolder
import java.util.concurrent.ConcurrentHashMap

/**
 * Browser session holder - one instance per thread
 */
class BrowserSession : AutoCloseable {

    val playwright: Playwright = Playwright.create(Playwright.CreateOptions().apply {
        setEnv(mapOf("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD" to "true"))
    })

    val browser: Browser = playwright.chromium().connect(ConfigHolder.getPlaywrightHost())

    override fun close() {
        try {
            browser.close()
        } catch (_: Exception) {
        }
        try {
            playwright.close()
        } catch (_: Exception) {
        }
    }
}

/**
 * Thread-local browser session manager
 */
class BrowserSessionManager {

    private val threadLocalSession = ThreadLocal.withInitial {
        val session = BrowserSession()
        activeSessions.add(session)
        session
    }

    val activeSessions: ConcurrentHashMap.KeySetView<BrowserSession, Boolean> = ConcurrentHashMap.newKeySet()

    fun getSession(): BrowserSession = threadLocalSession.get()

    fun close() {
        activeSessions.forEach { session ->
            session.close()
        }
        activeSessions.clear()
    }
}
