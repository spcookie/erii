package uesugi.spi.annotation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import uesugi.spi.Meta
import uesugi.spi.MetaToolSet

suspend fun useMeta(): Meta = currentCoroutineContext()[MetaElement]?.meta
    ?: error(NO_META_ERROR)

fun useToolMeta(): Lazy<Meta> = lazy { MetaToolSet.meta }

suspend fun withMeta(meta: Meta, block: suspend () -> Unit) {
    withContext(MetaElement(meta)) {
        block()
    }
}

suspend fun withMetaIO(meta: Meta, block: suspend () -> Unit) {
    withContext(Dispatchers.IO + MetaElement(meta)) {
        block()
    }
}
