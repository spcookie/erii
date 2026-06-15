package uesugi.core.component.browser

import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Page
import com.microsoft.playwright.Route
import com.microsoft.playwright.options.WaitUntilState
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import uesugi.common.toolkit.BrowserScraper
import uesugi.common.toolkit.ScrapedResult
import uesugi.common.toolkit.logger
import java.time.Duration
import kotlin.io.encoding.Base64

class BrowserScraperImpl : BrowserScraper {

    private val log = logger()

    private val jsonParser = Json { ignoreUnknownKeys = true }

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

    private val contextPool: GenericObjectPool<BrowserContext>

    init {
        val config = GenericObjectPoolConfig<BrowserContext>().apply {
            maxTotal = 2
            maxIdle = 2
            minIdle = 0
            blockWhenExhausted = true
            setMaxWait(Duration.ofMinutes(1))
            testOnBorrow = true
            testOnReturn = true
        }
        contextPool = GenericObjectPool(BrowserContextFactory(), config)
    }

    private class BrowserContextFactory : BasePooledObjectFactory<BrowserContext>() {
        override fun create(): BrowserContext =
            BrowserSession.getInstance().browser.newContext()

        override fun wrap(context: BrowserContext): PooledObject<BrowserContext> =
            DefaultPooledObject(context)

        override fun validateObject(p: PooledObject<BrowserContext>): Boolean =
            runCatching { p.getObject().pages(); true }.getOrDefault(false)

        override fun passivateObject(p: PooledObject<BrowserContext>) {
            val context = p.getObject()
            runCatching {
                context.pages().forEach { it.close() }
                context.clearCookies()
            }
        }

        override fun destroyObject(p: PooledObject<BrowserContext>) {
            runCatching { p.getObject().close() }
        }
    }

    private inline fun <R> useContext(block: (BrowserContext) -> R): R {
        val context = contextPool.borrowObject()
        return try {
            block(context)
        } finally {
            contextPool.returnObject(context)
        }
    }

    override fun takeFullScreenshot(
        url: String,
        width: Int,
        height: Int,
        quality: Int,
        type: BrowserScraper.ScreenshotType,
        waitForNetworkIdle: Boolean,
        username: String?,
        password: String?,
        scaleFactor: Double,
    ): ByteArray {
        return useContext { context ->
            val page = context.newPage()
            page.setViewportSize((width * scaleFactor).toInt(), (height * scaleFactor).toInt())

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

            // --- 滚动加载 ---
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

            page.screenshot(screenshotOptions)
        }
    }

    override fun scrape(url: String, maxMarkdownChars: Int): ScrapedResult {
        log.info("Scraping $url")

        return useContext { context ->
            val page = context.newPage()
            page.setViewportSize(1920, 1080)

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

                ScrapedResult(
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
                page.close()
            }
        }
    }

    override fun close() {
        runCatching { contextPool.close() }
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
