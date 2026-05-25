package uesugi.spi.annotation

import uesugi.spi.AgentPlugin
import uesugi.spi.PluginContext

abstract class PluginDelegate : AgentPlugin() {
    override fun start() {
        super.start()
        LifecycleRegistry.start(this)
    }

    override fun stop() {
        LifecycleRegistry.stop(this)
        super.stop()
    }
}
