package uesugi.toolkit

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import com.microsoft.playwright.options.ScreenshotType
import com.microsoft.playwright.options.WaitUntilState
import uesugi.common.logger
import kotlin.io.encoding.Base64

class WebScreenshotTaker : AutoCloseable {

    companion object {
        private val log = logger()
    }

    private val sessionManager = BrowserSessionManager()

    /**
     * 核心方法：URL -> 图片字节数组
     *
     * @param url 目标链接
     * @param width 视口宽度 (影响响应式布局，默认 1920)
     * @param quality 图片质量 (仅 JPEG 有效，PNG 为无损)
     * @param type 图片格式 (PNG 或 JPEG)
     * @param waitForNetworkIdle 是否等待网络空闲 (建议开启，防止图片没加载出来就截了)
     */
    fun takeFullScreenshot(
        url: String,
        width: Int = 1920,
        quality: Int = 70,
        deviceScaleFactor: Double = 1.0,
        type: ScreenshotType = ScreenshotType.JPEG, // JPEG 体积小，PNG 清晰但大
        waitForNetworkIdle: Boolean = true,
        username: String? = null,
        password: String? = null,
    ): ByteArray {
        val session = sessionManager.getSession()

        // 创建上下文，设置视口
        // 注意：全屏截图只需定宽，高度设为 0 或任意值均可，Playwright 会自动扩展
        session.browser.newContext(
            Browser.NewContextOptions()
                .setViewportSize(width, 1080)
                .setDeviceScaleFactor(deviceScaleFactor) // 1.0=标准, 2.0=高清(Retina)
        ).use { context ->
            try {
                val page = context.newPage()
                // --- 资源过滤优化 ---
                // 截图需要图片和CSS，但不需要媒体和字体
                var token: String? = null
                if (username != null && password != null) {
                    token = Base64.Default.encode("$username:$password".toByteArray())
                }
                page.route("**/*") { route ->
                    val headers = HashMap(route.request().headers())
                    if (token != null) {
                        headers["Authorization"] = "Basic $token"
                    }
                    val resourceType = route.request().resourceType()
                    if (listOf("media").contains(resourceType)) {
                        route.abort()
                    } else {
                        route.resume(Route.ResumeOptions().setHeaders(headers))
                    }
                }

                // --- 导航与等待 ---
                // 如果开启 waitForNetworkIdle，会等待直到网络连接数变少，适合 SPA/懒加载页面
                val waitState = if (waitForNetworkIdle)
                    WaitUntilState.NETWORKIDLE
                else
                    WaitUntilState.DOMCONTENTLOADED

                page.navigate(url, Page.NavigateOptions().setWaitUntil(waitState))

                // --- 滚动加载 (Lazy Loading 处理) ---
                // 很多现代网页图片是滚动到可见区域才加载的
                // 我们模拟快速滚动到底部，触发所有图片加载
                page.evaluate(
                    """
                async () => {
                    await new Promise((resolve) => {
                        let totalHeight = 0;
                        const distance = 300; // 每次滚 300px
                        const timer = setInterval(() => {
                            const scrollHeight = document.body.scrollHeight;
                            window.scrollBy(0, distance);
                            totalHeight += distance;

                            if(totalHeight >= scrollHeight){
                                clearInterval(timer);
                                // 滚到底后再滚回顶部，避免有些 sticky 元素挡住内容
                                window.scrollTo(0, 0);
                                resolve();
                            }
                        }, 50); // 每 50ms 滚一次
                    });
                }
            """
                )

                // 稍微等一下懒加载动画
                page.waitForTimeout(500.0)

                // --- 截图 ---
                val screenshotOptions = Page.ScreenshotOptions()
                    .setFullPage(true) // ✅ 关键：开启全屏截图
                    .setType(type)

                if (type == ScreenshotType.JPEG) {
                    screenshotOptions.setQuality(quality)
                }

                return page.screenshot(screenshotOptions)

            } catch (e: Exception) {
                log.error("Screenshot failed for $url: ${e.message}", e)
                throw e
            }
        }

    }

    override fun close() {
        sessionManager.close()
    }
}