package uesugi.common.toolkit

/**
 * 浏览器截图和网页抓取接口
 * 用于通过 Playwright 进行网页截图和内容提取
 */
interface BrowserScraper : AutoCloseable {

    /**
     * 对指定 URL 进行全屏截图
     *
     * @param url 目标链接
     * @param width 视口宽度 (影响响应式布局，默认 1920)
     * @param quality 图片质量 (仅 JPEG 有效，PNG 为无损)
     * @param type 图片格式 (PNG 或 JPEG)
     * @param waitForNetworkIdle 是否等待网络空闲 (建议开启，防止图片没加载出来就截了)
     * @param username 可选的 Basic Auth 用户名
     * @param password 可选的 Basic Auth 密码
     * @return 图片字节数组
     */
    fun takeFullScreenshot(
        url: String,
        width: Int = 1920,
        height: Int = 1080,
        quality: Int = 70,
        type: ScreenshotType = ScreenshotType.JPEG,
        waitForNetworkIdle: Boolean = true,
        username: String? = null,
        password: String? = null,
        scaleFactor: Double = 1.0,
    ): ByteArray

    /**
     * 对指定 URL 进行网页内容抓取并转换为 Markdown
     *
     * @param url 目标链接
     * @param maxMarkdownChars 最大 Markdown 字符数
     * @return 抓取结果
     */
    fun scrape(url: String, maxMarkdownChars: Int = 5000): ScrapedResult

    /**
     * 图片格式枚举
     */
    enum class ScreenshotType {
        PNG,
        JPEG
    }
}

/**
 * 网页抓取结果
 */
data class ScrapedResult(
    val url: String,
    val title: String,
    val excerpt: String,
    val markdown: String,
    val links: List<String> = emptyList(),
    val images: List<String> = emptyList()
)

/**
 * BrowserScraper 管理器，委托给 BrowserScraperProvider 实现
 * 在应用启动时通过 init() 注入实现
 */
object BrowserScraperHolder {

    private lateinit var provider: BrowserScraper

    fun init(provider: BrowserScraper) {
        this.provider = provider
    }

    fun getInstance(): BrowserScraper = provider
}


