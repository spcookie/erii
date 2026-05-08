package uesugi.core.route

import uesugi.common.BotManage
import uesugi.common.toolkit.ConfigHolder
import uesugi.spi.MetaToolSetCreator

interface RouteRule {
    val name: String
}

data class LLMRouteRule(override val name: String, val description: String) : RouteRule

// 简单的命令路由规则
data class CmdRouteRule(override val name: String) : RouteRule

object RouteRuleRegister {

    // 全局注册表: ruleName -> (pluginId, rule)
    private val rules = mutableMapOf<String, Pair<String, LLMRouteRule>>()

    fun addRule(name: String, description: String, pluginId: String) {
        rules[name] = pluginId to LLMRouteRule(name, description)
    }

    fun getRule(name: String): LLMRouteRule? {
        return rules[name]?.second
    }

    fun getAllRules(): List<LLMRouteRule> {
        return rules.values.map { it.second }.toList()
    }

    fun getRulesForBot(botId: String): List<LLMRouteRule> {
        val configKey = BotManage.getConfigKey(botId)
        return rules.filterValues { (pId, _) ->
            ConfigHolder.isPluginEnabled(configKey, pId)
        }.values.map { it.second }.toList()
    }
}

object CmdRuleRegister {
    // 全局注册表: cmdName -> (pluginId, rule)
    private val parsers = mutableMapOf<String, Pair<String, CmdRouteRule>>()

    fun addRule(name: String, pluginId: String, ruleName: String = name) {
        parsers[name] = pluginId to CmdRouteRule(ruleName)
    }

    fun getRule(name: String): CmdRouteRule? {
        return parsers[name]?.second
    }

    fun getRuleForBot(name: String, botId: String): CmdRouteRule? {
        val (pluginId, rule) = parsers[name] ?: return null
        val configKey = BotManage.getConfigKey(botId)
        return if (ConfigHolder.isPluginEnabled(configKey, pluginId)) rule else null
    }

    fun getAllRules(): List<CmdRouteRule> {
        return parsers.values.map { it.second }.distinctBy { it.name }.toList()
    }

    fun getRulesForBot(botId: String): List<CmdRouteRule> {
        val configKey = BotManage.getConfigKey(botId)
        return parsers.filterValues { (pId, _) ->
            ConfigHolder.isPluginEnabled(configKey, pId)
        }.values.map { it.second }.distinctBy { it.name }.toList()
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

    fun getToolSetsForBot(botId: String): List<MetaToolSetCreator> {
        val configKey = BotManage.getConfigKey(botId)
        return creators.filterKeys { pluginName ->
            ConfigHolder.isPluginEnabled(configKey, pluginName)
        }.values.toList()
    }
}
