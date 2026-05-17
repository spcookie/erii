package uesugi.core.component.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Playwright
import uesugi.common.toolkit.ConfigHolder

/**
 * Singleton browser session - shared across all threads
 */
class BrowserSession : AutoCloseable {

    val playwright: Playwright = Playwright.create(Playwright.CreateOptions().apply {
        setEnv(mapOf("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD" to (!ConfigHolder.getBrowserDownload()).toString()))
    })

    val browser: Browser = if (ConfigHolder.getBrowserDownload()) playwright.chromium().launch()
    else playwright.chromium().connect(ConfigHolder.getPlaywrightUrl())

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

    companion object {
        @Volatile
        private var instance: BrowserSession? = null

        fun getInstance(): BrowserSession {
            return instance ?: synchronized(this) {
                instance ?: BrowserSession().also { instance = it }
            }
        }

        fun close() {
            instance?.close()
            instance = null
        }
    }
}
