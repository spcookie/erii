package uesugi.core.plugin

import com.typesafe.config.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uesugi.common.ConfigHolder
import uesugi.spi.AgentExtension
import uesugi.spi.PluginConfig
import java.io.InputStream
import java.nio.file.Paths

internal class ConfigImpl(val plugin: AgentExtension<*>) : PluginConfig {

    override suspend fun readResource(path: String): InputStream {
        return withContext(Dispatchers.IO) {
            val path = Paths.get(path)
            val normalize = Paths.get(plugin::class.simpleName!!)
                .resolve(path)
                .normalize()
                .toString()
            plugin.javaClass
                .getResourceAsStream(normalize)
                ?: error("Resource not found: $normalize")
        }
    }

    private val config by lazy {
        ConfigHolder.getPluginConfig(plugin::class, plugin.name)
    }

    override fun invoke(): Config {
        return config
    }

}
