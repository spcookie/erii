package uesugi.core.plugin

import uesugi.spi.PluginDef
import uesugi.spi.RouteKey

internal class PluginDefImpl(
    override val name: String,
    override val routeKeys: List<RouteKey>
) : PluginDef
