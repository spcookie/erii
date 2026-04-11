package uesugi.plugin.animal

import org.pf4j.Extension
import uesugi.spi.ArgParserHolder
import uesugi.spi.CmdExtension
import uesugi.spi.PluginContext
import uesugi.spi.RouteExtension

@Extension
class AnimalExtension : RouteExtension<Animal>, CmdExtension<Unit, ArgParserHolder.Empty, Animal> {

    override val matcher: Pair<String, String>
        get() = TODO("Not yet implemented")

    override fun onLoad(context: PluginContext) {
        TODO("Not yet implemented")
    }

    override val cmd: String
        get() = TODO("Not yet implemented")

}