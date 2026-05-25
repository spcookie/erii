package uesugi.spi.annotation

import uesugi.spi.PluginContext

internal object ContextHolder {
    private val current = ThreadLocal<PluginContext>()

    fun set(ctx: PluginContext) { current.set(ctx) }
    fun get(): PluginContext = checkNotNull(current.get()) { "No active PluginContext" }
    fun clear() { current.remove() }
}
