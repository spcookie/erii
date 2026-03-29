package uesugi.core.plugin.buildin

import uesugi.spi.AgentExtension
import uesugi.spi.AgentPlugin

class Buildin : AgentPlugin()

interface BuildinExtension : AgentExtension<Buildin>