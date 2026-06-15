package uesugi.core.component.browser

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import java.time.Duration

class BrowserContextPool(
    private val browserSupplier: () -> Browser,
    maxTotal: Int = 2,
    maxWait: Duration = Duration.ofMinutes(1)
) : AutoCloseable {

    private val pool: GenericObjectPool<BrowserContext> = GenericObjectPool(
        BrowserContextFactory(),
        GenericObjectPoolConfig<BrowserContext>().apply {
            this.maxTotal = maxTotal
            maxIdle = maxTotal
            minIdle = 0
            blockWhenExhausted = true
            setMaxWait(maxWait)
            testOnBorrow = true
            testOnReturn = true
        }
    )

    fun <R> use(block: (BrowserContext) -> R): R {
        val context = pool.borrowObject()
        return try {
            block(context)
        } finally {
            pool.returnObject(context)
        }
    }

    override fun close() {
        runCatching { pool.close() }
    }

    private inner class BrowserContextFactory : BasePooledObjectFactory<BrowserContext>() {
        override fun create(): BrowserContext =
            browserSupplier().newContext()

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
}
