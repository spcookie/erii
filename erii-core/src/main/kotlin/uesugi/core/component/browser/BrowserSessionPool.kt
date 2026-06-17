package uesugi.core.component.browser

import org.apache.commons.pool2.BasePooledObjectFactory
import org.apache.commons.pool2.PooledObject
import org.apache.commons.pool2.impl.DefaultPooledObject
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import java.time.Duration

class BrowserSessionPool(
    maxTotal: Int = 2,
    maxWait: Duration = Duration.ofSeconds(30)
) : AutoCloseable {

    private val pool: GenericObjectPool<BrowserSession> = GenericObjectPool(
        BrowserSessionFactory(),
        GenericObjectPoolConfig<BrowserSession>().apply {
            this.maxTotal = maxTotal
            maxIdle = maxTotal
            minIdle = 1
            blockWhenExhausted = true
            setMaxWait(maxWait)
            testOnBorrow = true
            testOnReturn = false
            testWhileIdle = true
        }
    )

    fun <R> use(block: (BrowserSession) -> R): R {
        val session = pool.borrowObject()
        try {
            return block(session)
        } finally {
            pool.returnObject(session)
        }
    }

    override fun close() {
        pool.close()
    }

    private class BrowserSessionFactory : BasePooledObjectFactory<BrowserSession>() {
        override fun create(): BrowserSession = BrowserSession()

        override fun wrap(obj: BrowserSession): PooledObject<BrowserSession> =
            DefaultPooledObject(obj)

        override fun validateObject(p: PooledObject<BrowserSession>): Boolean =
            runCatching { p.getObject().isConnected }.getOrDefault(false)

        override fun destroyObject(p: PooledObject<BrowserSession>) {
            runCatching { p.getObject().close() }
        }
    }
}
