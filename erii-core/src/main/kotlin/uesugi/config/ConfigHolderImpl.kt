package uesugi.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigResolveOptions
import com.typesafe.config.ConfigValueFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.config.*
import uesugi.common.toolkit.BotConfig
import uesugi.common.toolkit.BotGroupsOverride
import uesugi.common.toolkit.ConfigProvider
import uesugi.common.toolkit.GroupConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class ConfigHolderImpl : ConfigProvider {

    private val log = KotlinLogging.logger {}

    private val pluginConfigCache = ConcurrentHashMap<String, Config>()

    private val configPath: String? by lazy {
        val raw = System.getProperty("config.path")
            ?: System.getenv("CONFIG_PATH")
            ?: "application.conf"
        File(raw).toPath().toAbsolutePath().toString()
    }

    private val pluginConfigDir: String? by lazy {
        val raw = System.getProperty("config.plugin.dir")
            ?: System.getenv("CONFIG_PLUGIN_DIR")
            ?: ((System.getProperty("pf4j.pluginsDir") ?: "plugins") + File.separator + "config")
        File(raw).toPath().toAbsolutePath().toString()
    }

    private val config: Config by lazy { loadConfig() }

    private val resolveOptions: ConfigResolveOptions
        get() = ConfigResolveOptions.defaults().setUseSystemEnvironment(true)

    private fun loadConfig(): Config {
        val classpathConfig = ConfigFactory.load("application.conf").resolve(resolveOptions)
        val fileConfig = configPath?.let { p ->
            ConfigFactory.parseFile(File(p)).resolve(resolveOptions)
        } ?: ConfigFactory.empty()
        val baseConfig = fileConfig.withFallback(classpathConfig)
        var cfg = baseConfig
        cfg = overrideWithSystemProperties(cfg)
        log.info { "Config loaded successfully" }
        return cfg
    }

    private fun overrideWithSystemProperties(base: Config): Config {
        val overrides = mutableMapOf<String, Any>()
        System.getProperties().stringPropertyNames()
            .forEach { key ->
                val value = System.getProperty(key)
                overrides[key] = value
            }
        if (overrides.isEmpty()) return base
        var overrideConfig = ConfigFactory.empty()
        overrides.forEach { (path, value) ->
            overrideConfig = overrideConfig.withValue(
                path,
                ConfigValueFactory.fromAnyRef(value, "system property: $path")
            )
        }
        return base.withFallback(overrideConfig)
    }

    private fun getLlmModelsHierarchical(provider: String): Map<String, String> {
        val hierarchicalPath = "llm.$provider.models"
        val hierarchicalConfig = config.getConfig(hierarchicalPath)
        val result = mutableMapOf<String, String>()
        // Read all tier keys from config
        hierarchicalConfig.root().keys.forEach { key ->
            val keyStr = key.toString()
            if (keyStr != "all") {
                result[keyStr] = hierarchicalConfig.getString(keyStr)
            }
        }
        // "all" override logic
        val allOverride = hierarchicalConfig.tryGetString("all")
        if (!allOverride.isNullOrBlank()) {
            result.keys.forEach { tier -> result[tier] = allOverride }
        }
        return result
    }

    override fun getLlmGoogleApiKey(): String = config.getString("llm.google.api-key")
    override fun getLlmGoogleBaseUrl(): String = config.getString("llm.google.base-url")
    override fun getLlmGoogleModels(): Map<String, String> = getLlmModelsHierarchical("google")

    override fun getLlmDeepSeekApiKey(): String = config.getString("llm.deep-seek.api-key")
    override fun getLlmDeepSeekBaseUrl(): String = config.getString("llm.deep-seek.base-url")
    override fun getLlmDeepSeekModels(): Map<String, String> =
        getLlmModelsHierarchical("deep-seek")

    override fun getLlmMinimaxApiKey(): String = config.getString("llm.minimax.api-key")
    override fun getLlmMinimaxBaseUrl(): String = config.getString("llm.minimax.base-url")
    override fun getLlmMinimaxModels(): Map<String, String> =
        getLlmModelsHierarchical("minimax")

    override fun getLlmOpenAIApiKey(): String = config.getString("llm.openai.api-key")
    override fun getLlmOpenAIBaseUrl(): String = config.getString("llm.openai.base-url")
    override fun getLlmOpenAIModels(): Map<String, String> =
        getLlmModelsHierarchical("openai")

    override fun getLlmAnthropicApiKey(): String = config.getString("llm.anthropic.api-key")
    override fun getLlmAnthropicBaseUrl(): String = config.getString("llm.anthropic.base-url")
    override fun getLlmAnthropicModels(): Map<String, String> =
        getLlmModelsHierarchical("anthropic")

    override fun getLlmOpenRouterApiKey(): String = config.getString("llm.openrouter.api-key")
    override fun getLlmOpenRouterBaseUrl(): String = config.getString("llm.openrouter.base-url")
    override fun getLlmOpenRouterModels(): Map<String, String> =
        getLlmModelsHierarchical("openrouter")

    override fun getChoiceProvider(): String = config.getString("llm.choice-provider")

    override fun getEmbeddingApiKey(): String = config.getString("embedding.api-key")
    override fun getEmbeddingProvider(): String = config.getString("embedding.provider")
    override fun getEmbeddingUrl(): String = config.getString("embedding.url")
    override fun getEmbeddingModel(): String = config.getString("embedding.model")

    override fun getSearchApiKey(): String = config.getString("search.api-key")
    override fun getSearchProvider(): String = config.getString("search.provider")
    override fun getSearchUrl(): String = config.getString("search.url")

    override fun getVisionApiKey(): String = config.getString("vision.api-key")
    override fun getVisionProvider(): String = config.getString("vision.provider")
    override fun getVisionUrl(): String = config.getString("vision.url")

    override fun getProxyHttp(): String? = config.tryGetString("proxy.http")
    override fun getProxySocks(): String? = config.tryGetString("proxy.socks")

    override fun getOnebotWs(): String = config.getString("onebot.ws")
    override fun getOnebotToken(): String = config.getString("onebot.token")

    override fun getOnebotBots(): Map<String, BotConfig> {
        return try {
            val botsConfig = config.getConfig("onebot.bots")
            val result = mutableMapOf<String, BotConfig>()
            botsConfig.root().keys.forEach { key ->
                val keyStr = key.toString()
                val botConfig = botsConfig.getConfig(keyStr)
                val groups = if (botConfig.hasPath("groups")) {
                    parseGroupsConfig(botConfig.getConfig("groups"))
                } else {
                    emptyMap()
                }
                result[keyStr] = BotConfig(
                    ws = botConfig.getString("ws"),
                    token = botConfig.getString("token"),
                    roleId = botConfig.getString("role-id"),
                    selfId = botConfig.tryGetString("self-id"),
                    groups = groups,
                    groupsOverride = if (botConfig.hasPath("groups-override")) {
                        parseBotGroupsOverride(botConfig.getConfig("groups-override"))
                    } else null,
                    enabledPlugins = if (botConfig.hasPath("enabled-plugins")) {
                        parseStringList(botConfig, "enabled-plugins")
                    } else null,
                    disabledPlugins = if (botConfig.hasPath("disabled-plugins")) {
                        parseStringList(botConfig, "disabled-plugins")
                    } else null,
                    serverHost = botConfig.tryGetString("server-host"),
                    externalHost = botConfig.tryGetString("external-host")
                )
            }
            result
        } catch (e: Exception) {
            log.warn { "Failed to load onebot bots config: ${e.message}" }
            emptyMap()
        }
    }

    private fun parseGroupsConfig(groupsConfig: Config): Map<String, GroupConfig> {
        val result = mutableMapOf<String, GroupConfig>()
        groupsConfig.root().keys.forEach { groupId ->
            val groupIdStr = groupId.toString()
            val admins = try {
                groupsConfig.getStringList("$groupIdStr.admins")
            } catch (_: Exception) {
                emptyList()
            }
            val desire = try {
                groupsConfig.getDouble("$groupIdStr.desire")
            } catch (_: Exception) {
                0.0
            }
            result[groupIdStr] = GroupConfig(admins = admins, desire = desire)
        }
        return result
    }

    private fun parseBotGroupsOverride(ov: Config): BotGroupsOverride {
        val enableGroups = if (ov.hasPath("enable-groups")) {
            try {
                val raw = ov.getString("enable-groups")
                if (raw.isNotBlank()) raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
                else emptyList()
            } catch (_: Exception) {
                try {
                    ov.getStringList("enable-groups").filter { it.isNotBlank() }
                } catch (_: Exception) {
                    null
                }
            }
        } else null

        val messageRedirectMap = if (ov.hasPath("message-redirect-map")) {
            try {
                val raw = ov.getString("message-redirect-map")
                if (raw.isNotBlank()) {
                    raw.split(",").map { it.trim() }
                        .filter { it.contains(":") }
                        .associate { val p = it.split(":"); p[0].trim() to p[1].trim() }
                } else emptyMap()
            } catch (_: Exception) {
                try {
                    val obj = ov.getObject("message-redirect-map")
                    obj.keys.associateWith { key -> ov.getString("message-redirect-map.$key") }
                        .filterValues { it.isNotBlank() }
                } catch (_: Exception) {
                    null
                }
            }
        } else null

        return BotGroupsOverride(
            enableGroups = enableGroups,
            debugGroupId = ov.tryGetString("debug-group-id"),
            messageRedirectMap = messageRedirectMap
        )
    }

    override fun getEffectiveEnableGroups(botKey: String): List<String> =
        (getOnebotBots()[botKey]?.groupsOverride?.enableGroups
            ?: getEnableGroups()) + ChatBridgeConst.MOCK_GROUP_ID.toString()

    override fun getEffectiveDebugGroupId(botKey: String): String? =
        getOnebotBots()[botKey]?.groupsOverride?.debugGroupId ?: getDebugGroupId()

    override fun getEffectiveMessageRedirectMap(botKey: String): Map<String, String> =
        getMessageRedirectMap() + (getOnebotBots()[botKey]?.groupsOverride?.messageRedirectMap ?: emptyMap())

    override fun getAdmins(botConfigKey: String, groupId: String): List<String> {
        val bots = getOnebotBots()
        val botConfig = bots[botConfigKey] ?: return emptyList()
        val groupConfig = botConfig.groups[groupId] ?: return emptyList()
        return groupConfig.admins
    }

    override fun getPlaywrightUrl(): String = config.getString("browser.playwright-url")

    override fun getBrowserDownload(): Boolean = config.getBoolean("browser.download")

    override fun getBrowserExternalHost(): String = config.tryGetString("browser.external-host") ?: "hostmachine"

    override fun getDebugGroupId(): String? = config.tryGetString("groups.debug-group-id")

    override fun getEnableGroups(): List<String> {
        return try {
            val raw = config.getString("groups.enable-groups")
            if (raw.isNotBlank()) {
                raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            try {
                config.getStringList("groups.enable-groups").filter { it.isNotBlank() }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    override fun getMessageRedirectMap(): Map<String, String> {
        return try {
            val raw = config.getString("groups.message-redirect-map")
            if (raw.isNotBlank()) {
                raw.split(",").map { it.trim() }
                    .filter { it.contains(":") }
                    .associate {
                        val parts = it.split(":")
                        parts[0].trim() to parts[1].trim()
                    }
            } else {
                emptyMap()
            }
        } catch (_: Exception) {
            try {
                val obj = config.getObject("groups.message-redirect-map")
                obj.keys.associateWith { key ->
                    config.getString("groups.message-redirect-map.$key")
                }.filterValues { it.isNotBlank() }
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

    // ===== 插件配置读取方法 =====
    // 优先级（从低到高）：resourcePath < pluginConfigDir < plugin.$pluginId.config（系统属性）
    // 高优先级配置覆盖低优先级配置

    override fun getPluginConfig(pluginClass: KClass<*>, pluginName: String): Config {
        return pluginConfigCache.computeIfAbsent(pluginName) {
            val pluginId = pluginName.substringBefore("_")

            // 1. 从 classpath 加载 resourcePath 作为基础配置
            var config = ConfigFactory.empty()
            val resourcePath = "plugin.json"
            val resourceAsStream = pluginClass.java.classLoader.getResourceAsStream(resourcePath)
            if (resourceAsStream != null) {
                log.info { "Loading base config for $pluginName from classpath: $resourcePath" }
                config = resourceAsStream.use { inputStream ->
                    ConfigFactory.parseReader(inputStream.reader()).resolve(resolveOptions)
                }
            }

            // 2. pluginConfigDir 中的配置覆盖基础配置
            if (pluginConfigDir != null) {
                val pluginConfigFile = File(pluginConfigDir, "$pluginId.json")
                if (pluginConfigFile.exists()) {
                    log.info { "Overriding config for $pluginId from dir: ${pluginConfigFile.absolutePath}" }
                    config = ConfigFactory.parseFile(pluginConfigFile).resolve(resolveOptions).withFallback(config)
                }
            }

            // 3. 系统属性 plugin.$pluginId.config 覆盖其他配置
            val customPath = System.getProperty("plugin.$pluginId.config")
            if (customPath != null) {
                log.info { "Overriding config for $pluginId with: $customPath" }
                config = ConfigFactory.parseFile(File(customPath)).resolve(resolveOptions).withFallback(config)
            }

            config
        }
    }

    private fun parseStringList(cfg: Config, path: String): List<String> {
        return try {
            val raw = cfg.getString(path)
            if (raw.isNotBlank()) raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            else emptyList()
        } catch (_: Exception) {
            try {
                cfg.getStringList(path).filter { it.isNotBlank() }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    override fun getEnabledPlugins(botKey: String): List<String>? =
        getOnebotBots()[botKey]?.enabledPlugins

    override fun getDisabledPlugins(botKey: String): List<String>? =
        getOnebotBots()[botKey]?.disabledPlugins

    override fun refresh() {
        pluginConfigCache.clear()
        log.info { "Plugin config cache cleared" }
    }

    override fun isPluginEnabled(botKey: String, pluginName: String): Boolean {
        // builtin 插件始终启用，忽略 disabled 配置
        if (pluginName.startsWith("builtin_", ignoreCase = true)) {
            return true
        }
        val enabled = getEnabledPlugins(botKey)
        val disabled = getDisabledPlugins(botKey) ?: emptyList()
        val matchShort = { short: String -> pluginName == short || pluginName.endsWith("_$short") }
        return when {
            enabled != null -> enabled.any(matchShort)
            else -> disabled.none(matchShort)
        }
    }
}
