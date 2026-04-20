package uesugi.core.component.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import com.microsoft.playwright.options.WaitUntilState
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import uesugi.common.toolkit.BrowserScraper
import uesugi.common.toolkit.ScrapedResult
import uesugi.common.toolkit.logger
import kotlin.io.encoding.Base64

class BrowserScraperImpl : BrowserScraper {

    private val log = logger()

    private val jsonParser = Json { ignoreUnknownKeys = true }

    private val lock = Any()

    private val readabilityJs: String by lazy {
        this::class.java.getResource("readability.js")?.readText()
            ?: throw IllegalStateException("readability.js not found!")
    }

    private val mdConverter: FlexmarkHtmlConverter by lazy {
        val options = MutableDataSet().apply {
            set(FlexmarkHtmlConverter.SKIP_ATTRIBUTES, true)
            set(FlexmarkHtmlConverter.WRAP_AUTO_LINKS, true)
            set(FlexmarkHtmlConverter.MAX_BLANK_LINES, 2)
            set(Parser.EXTENSIONS, listOf(TablesExtension.create()))
            set(TablesExtension.TRIM_CELL_WHITESPACE, true)
            set(TablesExtension.MIN_HEADER_ROWS, 1)
        }
        FlexmarkHtmlConverter.builder(options).build()
    }

    override fun takeFullScreenshot(
        url: String,
        width: Int,
        quality: Int,
        type: BrowserScraper.ScreenshotType,
        waitForNetworkIdle: Boolean,
        username: String?,
        password: String?
    ): ByteArray {
        val session = BrowserSession.getInstance()

        synchronized(lock) {
            session.browser.newContext(
                Browser.NewContextOptions()
                    .setViewportSize(width, 1080)
                    .setDeviceScaleFactor(1.0)
            ).use { context ->
                try {
                    val page = context.newPage()
                    // --- 资源过滤优化 ---
                    var token: String? = null
                    if (username != null && password != null) {
                        token = Base64.encode("$username:$password".toByteArray())
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
                    val waitState = if (waitForNetworkIdle)
                        WaitUntilState.NETWORKIDLE
                    else
                        WaitUntilState.DOMCONTENTLOADED

                    page.navigate(url, Page.NavigateOptions().setWaitUntil(waitState))

                    // --- 滚动加载 (Lazy Loading 处理) ---
                    page.evaluate(
                        """
                async () => {
                    await new Promise((resolve) => {
                        let totalHeight = 0;
                        const distance = 300;
                        const timer = setInterval(() => {
                            const scrollHeight = document.body.scrollHeight;
                            window.scrollBy(0, distance);
                            totalHeight += distance;

                            if(totalHeight >= scrollHeight){
                                clearInterval(timer);
                                window.scrollTo(0, 0);
                                resolve();
                            }
                        }, 50);
                    });
                }
            """
                    )

                    page.waitForTimeout(500.0)

                    // --- 截图 ---
                    val screenshotOptions = Page.ScreenshotOptions()
                        .setFullPage(true)
                        .setType(
                            when (type) {
                                BrowserScraper.ScreenshotType.PNG -> com.microsoft.playwright.options.ScreenshotType.PNG
                                BrowserScraper.ScreenshotType.JPEG -> com.microsoft.playwright.options.ScreenshotType.JPEG
                            }
                        )

                    if (type == BrowserScraper.ScreenshotType.JPEG) {
                        screenshotOptions.setQuality(quality)
                    }

                    return page.screenshot(screenshotOptions)

                } catch (e: Exception) {
                    log.error("Screenshot failed for $url: ${e.message}", e)
                    throw e
                }
            }
        }
    }

    override fun scrape(url: String, maxMarkdownChars: Int): ScrapedResult {
        log.info("Scraping $url")

        val session = BrowserSession.getInstance()

        synchronized(lock) {
            val context = session.browser.newContext(
                Browser.NewContextOptions()
                    .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .setViewportSize(1920, 1080)
            )

            val page = context.newPage()

            try {
                page.route("**/*") { route ->
                    val type = route.request().resourceType()
                    if (listOf("image", "stylesheet", "font", "media").contains(type)) {
                        route.abort()
                    } else {
                        route.resume()
                    }
                }

                page.navigate(url, Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED))

                page.evaluate(readabilityJs)

                val jsonResultString = page.evaluate(
                    """
                () => {
                    try {
                        if (typeof Readability === 'undefined') return JSON.stringify({ error: "Readability injection failed" });

                        const documentClone = document.cloneNode(true);
                        const article = new Readability(documentClone).parse();

                        if (!article) return JSON.stringify({ error: "Readability returned null" });

                        const tempDiv = document.createElement('div');
                        tempDiv.innerHTML = article.content;

                        const imgs = Array.from(tempDiv.querySelectorAll('img'));
                        const imgUrls = imgs.map(img => img.src).filter(src => src).filter((v, i, a) => a.indexOf(v) === i);
                        article.extractedImages = imgUrls;
                        imgs.forEach(img => img.remove());

                        const links = Array.from(tempDiv.querySelectorAll('a[href]'))
                            .map(a => a.href).filter((v, i, a) => a.indexOf(v) === i);
                        article.extractedUrls = links;

                        tempDiv.querySelectorAll('a').forEach(a => {
                            if (a.childNodes.length > 0) a.replaceWith(...a.childNodes);
                            else a.remove();
                        });

                        tempDiv.querySelectorAll('iframe, object, embed, video, audio, svg, button, script, style').forEach(el => el.remove());

                        article.content = tempDiv.innerHTML;
                        return JSON.stringify(article);
                    } catch (e) {
                        return JSON.stringify({ error: "JS Error: " + e.toString() });
                    }
                }
            """
                ) as String

                val response = try {
                    jsonParser.decodeFromString<ReadabilityResponse>(jsonResultString)
                } catch (e: Exception) {
                    throw RuntimeException("JSON Parse Error", e)
                }

                if (response.error != null) {
                    throw RuntimeException("Browser Error: ${response.error}")
                }

                val htmlContent = response.content ?: ""
                val fullMarkdown = if (htmlContent.isNotBlank()) {
                    mdConverter.convert(htmlContent)
                } else {
                    ""
                }

                log.info("scraping completed for $url, markdown: ${fullMarkdown.take(50)}")

                val finalMarkdown = if (fullMarkdown.length > maxMarkdownChars) {
                    fullMarkdown.take(maxMarkdownChars) + "\n...(内容过长，已截断)..."
                } else {
                    fullMarkdown
                }

                return ScrapedResult(
                    url = url,
                    title = response.title ?: "No Title",
                    excerpt = response.excerpt ?: "",
                    markdown = finalMarkdown,
                    links = response.extractedUrls,
                    images = response.extractedImages
                )

            } catch (e: Exception) {
                log.warn("Scraping failed for $url: ${e.message}")
                throw e
            } finally {
                context.close()
            }
        }
    }

    override fun close() {
        BrowserSession.close()
    }

    @Serializable
    private data class ReadabilityResponse(
        val title: String? = null,
        val content: String? = null,
        val excerpt: String? = null,
        val extractedUrls: List<String> = emptyList(),
        val extractedImages: List<String> = emptyList(),
        val error: String? = null
    )
}
