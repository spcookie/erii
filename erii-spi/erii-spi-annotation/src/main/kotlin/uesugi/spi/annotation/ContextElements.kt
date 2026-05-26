package uesugi.spi.annotation

import uesugi.spi.Meta
import uesugi.spi.PluginContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

internal const val NO_META_ERROR = "No active Meta — are you in a handler context?"
internal const val NO_CONTEXT_ERROR = "No active PluginContext — are you in a handler context?"

internal class MetaElement(val meta: Meta) : AbstractCoroutineContextElement(MetaElement) {
    companion object Key : CoroutineContext.Key<MetaElement>
}

internal class PluginContextElement(val context: PluginContext) :
    AbstractCoroutineContextElement(PluginContextElement) {
    companion object Key : CoroutineContext.Key<PluginContextElement>
}
