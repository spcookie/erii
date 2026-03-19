package speech

import org.pf4j.Extension
import uesugi.spi.AgentExtension
import uesugi.spi.AgentPlugin
import uesugi.spi.PluginContext

class Speech : AgentPlugin<Speech.SpeechExtension>() {
    override val extension: AgentExtension
        get() = SpeechExtension()

    @Extension
    class SpeechExtension : AgentExtension {
        override val name: String
            get() = TODO("Not yet implemented")

        override fun onLoad(context: PluginContext) {
            TODO("Not yet implemented")
        }

    }
}