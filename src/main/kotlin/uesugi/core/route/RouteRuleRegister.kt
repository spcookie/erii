package uesugi.core.route

import kotlinx.cli.ArgParser
import uesugi.core.plugin.MetaToolSetCreator

interface RouteRule {
    val name: String
}

data class LLMRouteRule(override val name: String, val description: String) : RouteRule

object RouteRuleRegister {

    private val rules = mutableMapOf<String, LLMRouteRule>()

    fun addRule(name: String, description: String) {
        rules[name] = LLMRouteRule(name, description)
    }

    fun getRule(name: String): LLMRouteRule? {
        return rules[name]
    }

    fun getAllRules(): List<LLMRouteRule> {
        return rules.values.toList()
    }
}

data class CmdRouteRule(override val name: String, val argParser: ArgParser) : RouteRule

object CmdRuleRegister {
    private val parsers = mutableMapOf<String, CmdRouteRule>()

    fun addRule(name: String, argParser: ArgParser) {
        parsers[name] = CmdRouteRule(name, argParser)
    }

    fun getRule(name: String): CmdRouteRule? {
        return parsers[name]
    }

    fun getAllRules(): List<CmdRouteRule> {
        return parsers.values.toList()
    }
}

object MetaToolSetRegister {
    private val creators = mutableMapOf<String, MetaToolSetCreator>()

    fun addToolSet(name: String, creator: MetaToolSetCreator) {
        creators[name] = creator
    }

    fun getToolSet(name: String): (MetaToolSetCreator)? {
        return creators[name]
    }

    fun getAllToolSets(): List<MetaToolSetCreator> {
        return creators.values.toList()
    }
}