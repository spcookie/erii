package uesugi.toolkit

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.WaitUntilState
import com.vladsch.flexmark.ext.tables.TablesExtension
import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.util.data.MutableDataSet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

class WebPageMarkdownScraper : AutoCloseable {

    companion object {

        private val log = logger()

        // 全局共享的 Markdown 转换器（它是线程安全的，不需要每个线程一个）
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
    }


    // 内部类：持有每个线程独立的浏览器实例
    private class BrowserSession : AutoCloseable {
        val playwright: Playwright = Playwright.create()
        val browser: Browser = playwright.chromium().connect(System.getenv("PLAYWRIGHT_HOST"))

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

    // 使用 ThreadLocal 确保每个线程获取到自己的 Session
    private val threadLocalSession = ThreadLocal.withInitial {
        val session = BrowserSession()
        // 注册到全局集合中，以便 scraper.close() 时能关闭所有线程的浏览器
        activeSessions.add(session)
        session
    }

    // 用于记录所有活跃的 session，以便统一关闭
    private val activeSessions = ConcurrentHashMap.newKeySet<BrowserSession>()

    private val readabilityJs: String by lazy {
        this::class.java.getResource("/js/readability.js")?.readText()
            ?: throw IllegalStateException("readability.js not found!")
    }

    // 全局 JSON 配置
    private val jsonParser = Json { ignoreUnknownKeys = true }

    /**
     * URL -> Markdown
     */
    fun scrape(url: String, maxMarkdownChars: Int = 5000): ScrapedResult {
        log.info("Scraping $url")

        // 1. 获取当前线程绑定的浏览器会话
        val session = threadLocalSession.get()

        // 2. 在当前线程的浏览器中创建上下文
        val context = session.browser.newContext(
            Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setViewportSize(1920, 1080)
        )

        val page = context.newPage()

        try {
            // 拦截资源
            page.route("**/*") { route ->
                val type = route.request().resourceType()
                if (listOf("image", "stylesheet", "font", "media").contains(type)) {
                    route.abort()
                } else {
                    route.resume()
                }
            }

            page.navigate(url, Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED))

            // 注入脚本
            page.evaluate(readabilityJs)

            // 执行 JS 提取
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

                        // 图片提取
                        const imgs = Array.from(tempDiv.querySelectorAll('img'));
                        const imgUrls = imgs.map(img => img.src).filter(src => src).filter((v, i, a) => a.indexOf(v) === i);
                        article.extractedImages = imgUrls;
                        imgs.forEach(img => img.remove());

                        // 链接提取与去壳
                        const links = Array.from(tempDiv.querySelectorAll('a[href]'))
                            .map(a => a.href).filter((v, i, a) => a.indexOf(v) === i);
                        article.extractedUrls = links;
                        
                        tempDiv.querySelectorAll('a').forEach(a => {
                            if (a.childNodes.length > 0) a.replaceWith(...a.childNodes);
                            else a.remove();
                        });

                        // 清理干扰项
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

            // 截断逻辑
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
            // 务必关闭 Context，否则内存泄漏
            // 注意：不要关闭 session.browser，因为它是 ThreadLocal 复用的
            context.close()
        }
    }

    /**
     * 关闭资源时，遍历所有线程创建的 BrowserSession 并关闭
     */
    override fun close() {
        activeSessions.forEach { session ->
            session.close()
        }
        activeSessions.clear()
    }
}

// 数据类保持不变
data class ScrapedResult(
    val url: String,
    val title: String,
    val excerpt: String,
    val markdown: String,
    val links: List<String> = emptyList(),
    val images: List<String> = emptyList()
)

@Serializable
data class ReadabilityResponse(
    val title: String? = null,
    val content: String? = null,
    val excerpt: String? = null,
    val extractedUrls: List<String> = emptyList(),
    val extractedImages: List<String> = emptyList(),
    val error: String? = null
)