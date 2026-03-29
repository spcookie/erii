package uesugi.core.plugin.builtin

import uesugi.spi.AgentExtension
import uesugi.spi.AgentPlugin

class Builtin : AgentPlugin()

interface BuiltinExtension : AgentExtension<Builtin>