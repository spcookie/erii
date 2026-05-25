package uesugi.spi.annotation

import uesugi.spi.Meta
import uesugi.spi.MetaToolSet
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext

private object MetaContext : CoroutineContext.Key<MetaElement>
private class MetaElement(val meta: Meta) : CoroutineContext.Element {
    override val key = MetaContext
}

fun Meta.asContextElement(): CoroutineContext.Element = MetaElement(this)

suspend fun meta(): Meta = checkNotNull(currentCoroutineContext()[MetaContext]?.meta) {
    "meta() can only be called within a handler context"
}

fun useMeta(): Lazy<Meta> = lazy { MetaToolSet.meta }
