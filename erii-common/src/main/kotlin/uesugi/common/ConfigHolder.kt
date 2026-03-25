package uesugi.common

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.server.config.*
import java.io.File

/**
 * 配置管理器，使用 Typesafe Config 读取配置文件
 * 支持通过 -D 参数覆盖配置
 *
 * -Dconfig.path=/path/to/application.conf  # 指定配置文件路径
 */
object ConfigHolder {

    private val log = KotlinLogging.logger {}

    /**
     * 配置文件路径，可通过 -Dconfig.path 指定
     */
    private val configPath: String by lazy {
        System.getProperty("config.path") ?: "application.conf"
    }

    /**
     * 配置文件目录（用于插件配置）
     */
    private val configDir: String by lazy {
        System.getProperty("config.plugin.dir") ?: File(configPath).parentFile?.absolutePath ?: "."
    }

    /**
     * 主配置对象 (app.*)
     */
    val app: Config by lazy { loadAppConfig() }

    /**
     * 插件配置 (plugins.*)
     */
    val plugins: Config by lazy { loadPluginsConfig() }

    private fun loadAppConfig(): Config {
        // 1. 首先加载配置文件（支持 -Dconfig.path 指定路径）
        // ConfigFactory.load() 会自动解析 ${ENV_VAR} 占位符
        log.info { "Loading config from: $configPath" }
        val baseConfig = if (File(configPath).isAbsolute) {
            ConfigFactory.parseFile(File(configPath))
        } else {
            ConfigFactory.load(configPath)
        }
        var config = baseConfig.getConfig("app")

        // 2. 检查 -D 参数覆盖
        config = overrideWithSystemProperties(config, "app")

        log.info { "App config loaded successfully" }
        return config
    }

    private fun loadPluginsConfig(): Config {
        // 1. 首先加载配置文件中的 plugins 部分
        val baseConfig = if (File(configPath).isAbsolute) {
            ConfigFactory.parseFile(File(configPath))
        } else {
            ConfigFactory.load(configPath)
        }
        var config = baseConfig.getConfig("plugins")

        // 2. 检查 -D 参数覆盖
        config = overrideWithSystemProperties(config, "plugins")

        log.info { "Plugins config loaded successfully" }
        return config
    }

    /**
     * 从 -D 参数覆盖配置
     * 支持格式: -Dapp.llm.google-api-key=xxx
     */
    private fun overrideWithSystemProperties(base: Config, prefix: String): Config {
        val overrides = mutableMapOf<String, Any>()

        // 遍历所有系统属性，寻找以指定前缀开头的
        System.getProperties().stringPropertyNames()
            .filter { it.startsWith("$prefix.") }
            .forEach { key ->
                val configPath = key.removePrefix("$prefix.")
                val value = System.getProperty(key)
                overrides[configPath] = value
                log.info { "Override config: $key = $value" }
            }

        if (overrides.isEmpty()) return base

        // 构建覆盖配置
        var overrideConfig = ConfigFactory.empty()
        overrides.forEach { (path, value) ->
            overrideConfig = overrideConfig.withValue(
                path,
                ConfigValueFactory.fromAnyRef(value, "system property: $path")
            )
        }

        return base.withFallback(overrideConfig)
    }

    // ===== App 配置读取方法 =====

    fun getLlmGoogleApiKey(): String = app.getString("llm.google-api-key")
    fun getLlmGoogleBaseUrl(): String =
        app.tryGetString("llm.google-base-url") ?: "https://generativelanguage.googleapis.com"

    fun getLlmDeepSeekApiKey(): String = app.getString("llm.deep-seek-api-key")
    fun getLlmDeepSeekBaseUrl(): String =
        app.tryGetString("llm.deep-seek-base-url") ?: "https://api.deepseek.com"

    fun getLlmMinimaxApiKey(): String = app.getString("llm.minimax-coding-plan-key")
    fun getLlmMinimaxBaseUrl(): String =
        app.tryGetString("llm.minimax-base-url") ?: "https://api.minimaxi.com"

    fun getChoiceModel(): String = app.getString("llm.choice-model")

    fun getEmbeddingApiKey(): String = app.getString("embedding.api-key")
    fun getExaApiKey(): String = app.getString("exa.api-key")

    fun getProxyHttp(): String? = app.tryGetString("proxy.http")
    fun getProxySocks(): String? = app.tryGetString("proxy.socks")

    fun getNapcatWs(): String = app.getString("napcat.ws")
    fun getNapcatToken(): String = app.getString("napcat.token")

    /**
     * 获取所有 bot 配置
     * @return Map<botKey, BotConfig(ws, token, roleId)>
     */
    fun getNapcatBots(): Map<String, BotConfig> {
        return try {
            val botsConfig = app.getConfig("napcat.bots")
            val result = mutableMapOf<String, BotConfig>()
            botsConfig.root().keys.forEach { key ->
                val keyStr = key.toString()
                result[keyStr] = BotConfig(
                    ws = botsConfig.getString("$keyStr.ws"),
                    token = botsConfig.getString("$keyStr.token"),
                    roleId = botsConfig.getString("$keyStr.role-id")
                )
            }
            result
        } catch (e: Exception) {
            log.warn { "Failed to load napcat bots config: ${e.message}" }
            emptyMap()
        }
    }

    /**
     * Bot 配置数据类
     */
    data class BotConfig(
        val ws: String,
        val token: String,
        val roleId: String
    )

    fun getPlaywrightHost(): String = app.getString("web.playwright-host")
    fun getPlaywrightSkipBrowserDownload(): Boolean = app.getBoolean("web.playwright-skip-browser-download")

    fun getDebugGroupId(): String? = app.tryGetString("groups.debug-group-id")

    fun getEnableGroups(): List<String> {
        return try {
            // 优先尝试获取字符串列表（从环境变量传入的逗号分隔字符串）
            val raw = app.getString("groups.enable-groups")
            if (raw.isNotBlank()) {
                raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            try {
                app.getStringList("groups.enable-groups").filter { it.isNotBlank() }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun getMessageRedirectMap(): Map<String, String> {
        return try {
            // 优先尝试获取字符串（从环境变量传入的 "key:value,key:value" 格式）
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
        } catch (e: Exception) {
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

    /**
     * 获取单个插件的配置
     * 加载顺序：
     * 1. -Dplugin.{PluginName}.config 参数指定的文件
     * 2. -Dconfig.plugin.dir 参数指定目录下的 {PluginName}.conf 文件
     * 3. classpath 中的 /plugin/{PluginName}.conf 文件
     * 4. 主配置文件 application.conf 中的 plugins.{PluginName} 部分
     *
     * @param pluginClass 插件类，用于从 classpath 加载配置
     * @param pluginName 插件名称
     */
    fun getPluginConfig(pluginClass: Class<*>, pluginName: String): Config {
        // 1. 检查 -Dplugin.{PluginName}.config 参数
        val customPath = System.getProperty("plugin.$pluginName.config")
        if (customPath != null) {
            log.info { "Loading custom config for $pluginName from: $customPath" }
            return ConfigFactory.parseFile(File(customPath))
        }

        // 2. 检查 -Dconfig.plugin.dir 参数
        val pluginConfigDir = System.getProperty("config.plugin.dir")
        if (pluginConfigDir != null) {
            val pluginConfigFile = File(pluginConfigDir, "$pluginName.conf")
            if (pluginConfigFile.exists()) {
                log.info { "Loading config for $pluginName from dir: ${pluginConfigFile.absolutePath}" }
                return ConfigFactory.parseFile(pluginConfigFile)
            }
        }

        // 3. 从 classpath 读取 resources/plugin.conf
        val resourcePath = "plugin.conf"
        val resourceAsStream = pluginClass.getResourceAsStream(resourcePath)
        if (resourceAsStream != null) {
            log.info { "Loading config for $pluginName from classpath: $resourcePath" }
            return resourceAsStream.use { inputStream ->
                ConfigFactory.parseReader(inputStream.reader())
            }
        }

        // 4. 从主配置文件的 plugins 部分读取
        return try {
            plugins.getConfig(pluginName)
        } catch (_: Exception) {
            log.warn { "Plugin config not found for: $pluginName" }
            ConfigFactory.empty()
        }
    }

}