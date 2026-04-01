package uesugi.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.config.*
import uesugi.common.BotConfig
import uesugi.common.ConfigProvider
import uesugi.common.GroupConfig
import java.io.File
import kotlin.reflect.KClass

class ConfigHolderImpl : ConfigProvider {

    private val log = KotlinLogging.logger {}

    private val configPath: String? by lazy {
        System.getProperty("config.path")
            ?: System.getenv("CONFIG_PATH")
            ?: "application.conf"
    }

    val app: Config by lazy { loadAppConfig() }
    val plugins: Config by lazy { loadPluginsConfig() }

    private fun loadAppConfig(): Config {
        return loadConfig("app") { log.info { "App config loaded successfully" } }
    }

    private fun loadPluginsConfig(): Config {
        return loadConfig("plugins") { log.info { "Plugins config loaded successfully" } }
    }

    private fun loadConfig(path: String, onSuccess: () -> Unit): Config {
        val classpathConfig = ConfigFactory.load("application.conf").resolve()
        val fileConfig = configPath?.takeIf { it != "application.conf" }?.let { p ->
            if (File(p).isAbsolute) {
                ConfigFactory.parseFile(File(p)).resolve()
            } else {
                ConfigFactory.load(p).resolve()
            }
        } ?: ConfigFactory.empty()
        val baseConfig = classpathConfig.withFallback(fileConfig)
        var config = baseConfig.getConfig(path)
        config = overrideWithSystemProperties(config, path)
        onSuccess()
        return config
    }

    private fun overrideWithSystemProperties(base: Config, prefix: String): Config {
        val overrides = mutableMapOf<String, Any>()
        System.getProperties().stringPropertyNames()
            .filter { it.startsWith("$prefix.") }
            .forEach { key ->
                val configPath = key.removePrefix("$prefix.")
                val value = System.getProperty(key)
                overrides[configPath] = value
                log.info { "Override config: $key = $value" }
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

    override fun getLlmGoogleApiKey(): String = app.getString("llm.google-api-key")
    override fun getLlmGoogleBaseUrl(): String =
        app.tryGetString("llm.google-base-url") ?: "https://generativelanguage.googleapis.com"

    override fun getLlmDeepSeekApiKey(): String = app.getString("llm.deep-seek-api-key")
    override fun getLlmDeepSeekBaseUrl(): String =
        app.tryGetString("llm.deep-seek-base-url") ?: "https://api.deepseek.com"

    override fun getLlmMinimaxApiKey(): String = app.getString("llm.minimax-coding-plan-key")
    override fun getLlmMinimaxBaseUrl(): String =
        app.tryGetString("llm.minimax-base-url") ?: "https://api.minimaxi.com"

    override fun getChoiceModel(): String = app.getString("llm.choice-model")

    override fun getEmbeddingApiKey(): String = app.getString("embedding.api-key")
    override fun getEmbeddingProvider(): String =
        app.tryGetString("embedding.provider") ?: "bytedance"

    override fun getSearchApiKey(): String = app.getString("search.api-key")
    override fun getSearchProvider(): String =
        app.tryGetString("search.provider") ?: "exa"

    override fun getProxyHttp(): String? = app.tryGetString("proxy.http")
    override fun getProxySocks(): String? = app.tryGetString("proxy.socks")

    override fun getOnebotWs(): String = app.getString("onebot.ws")
    override fun getOnebotHttp(): String = app.getString("onebot.http")
    override fun getOnebotToken(): String = app.getString("onebot.token")

    override fun getOnebotBots(): Map<String, BotConfig> {
        return try {
            val botsConfig = app.getConfig("onebot.bots")
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
                    groups = groups
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
            result[groupIdStr] = GroupConfig(admins = admins)
        }
        return result
    }

    override fun getAdmins(botConfigKey: String, groupId: String): List<String> {
        val bots = getOnebotBots()
        val botConfig = bots[botConfigKey] ?: return emptyList()
        val groupConfig = botConfig.groups[groupId] ?: return emptyList()
        return groupConfig.admins
    }

    override fun getPlaywrightHost(): String = app.getString("browser.playwright-host")

    override fun getDebugGroupId(): String? = app.tryGetString("groups.debug-group-id")

    override fun getEnableGroups(): List<String> {
        return try {
            val raw = app.getString("groups.enable-groups")
            if (raw.isNotBlank()) {
                raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            try {
                app.getStringList("groups.enable-groups").filter { it.isNotBlank() }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    override fun getMessageRedirectMap(): Map<String, String> {
        return try {
            val raw = app.getString("groups.message-redirect-map")
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
                val obj = app.getObject("groups.message-redirect-map")
                obj.keys.associateWith { key ->
                    app.getString("groups.message-redirect-map.$key")
                }.filterValues { it.isNotBlank() }
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

    // ===== 插件配置读取方法 =====

    override fun getPluginConfig(pluginClass: KClass<*>, pluginName: String): Config {
        val pluginId = pluginName.substringBeforeLast("_")
        val customPath = System.getProperty("plugin.$pluginId.config")
        if (customPath != null) {
            log.info { "Loading custom config for $pluginId from: $customPath" }
            return ConfigFactory.parseFile(File(customPath)).resolve()
        }
        val pluginConfigDir = System.getProperty("config.plugin.dir")
        if (pluginConfigDir != null) {
            val pluginConfigFile = File(pluginConfigDir, "$pluginId.conf")
            if (pluginConfigFile.exists()) {
                log.info { "Loading config for $pluginId from dir: ${pluginConfigFile.absolutePath}" }
                return ConfigFactory.parseFile(pluginConfigFile).resolve()
            }
        }
        val resourcePath = "plugin.conf"
        val resourceAsStream = pluginClass.java.classLoader.getResourceAsStream(resourcePath)
        if (resourceAsStream != null) {
            log.info { "Loading config for $pluginName from classpath: $resourcePath" }
            return resourceAsStream.use { inputStream ->
                ConfigFactory.parseReader(inputStream.reader()).resolve()
            }
        }
        return try {
            plugins.getConfig(pluginId)
        } catch (_: Exception) {
            log.warn { "Plugin config not found for: $pluginId" }
            ConfigFactory.empty()
        }
    }
}
