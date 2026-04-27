package uesugi.core.component.vision

import uesugi.common.extend.IVision
import uesugi.common.toolkit.ConfigHolder
import java.util.*

object VisionManager {
    private val providers: Map<String, IVision> by lazy {
        ServiceLoader.load(IVision::class.java).associateBy { it.id }
    }

    fun get(): IVision {
        val id = ConfigHolder.getVisionProvider()
        return providers[id] ?: error("No vision provider found for id: $id, available: ${providers.keys}")
    }
}