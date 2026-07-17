package uesugi.plugin

import uesugi.spi.PluginDef
import uesugi.spi.RouteKey

internal class PluginDefImpl(
    override val name: String,
    routeKeysProvider: () -> List<RouteKey>,
) : PluginDef {
    override val routeKeys: List<RouteKey> by lazy(
        LazyThreadSafetyMode.PUBLICATION,
        routeKeysProvider,
    )
}
