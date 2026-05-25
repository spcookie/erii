package uesugi.spi.annotation

import uesugi.spi.PluginContext

fun onStart(handler: suspend (PluginContext) -> Unit) = LifecycleRegistry.onStart(handler)
fun onStop(handler: suspend () -> Unit) = LifecycleRegistry.onStop(handler)
