package uesugi.core.component.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Playwright
import uesugi.common.toolkit.ConfigHolder

class BrowserSession : AutoCloseable {

    val playwright: Playwright = Playwright.create(Playwright.CreateOptions().apply {
        setEnv(mapOf("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD" to (!ConfigHolder.getBrowserDownload()).toString()))
    })

    val browser: Browser = if (ConfigHolder.getBrowserDownload()) playwright.chromium().launch()
    else playwright.chromium().connect(ConfigHolder.getPlaywrightUrl())

    val isConnected: Boolean get() = runCatching { browser.isConnected }.getOrDefault(false)

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
