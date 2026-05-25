package uesugi.spi.annotation

import uesugi.spi.Meta
import uesugi.spi.MetaContext
import uesugi.spi.MetaToolSet
import uesugi.spi.asContextElement
import kotlinx.coroutines.currentCoroutineContext

suspend fun meta(): Meta = checkNotNull(currentCoroutineContext()[MetaContext]?.meta) {
    "meta() can only be called within a handler context"
}

fun useMeta(): Lazy<Meta> = lazy { MetaToolSet.meta }
