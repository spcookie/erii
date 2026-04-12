package uesugi.common.toolkit

import com.typesafe.config.Config
import kotlin.reflect.KClass

/**
 * 群组配置数据类
 */
data class GroupConfig(val admins: List<String>)

/**
 * Bot 配置数据类
 */
data class BotConfig(
    val ws: String,
    val token: String,
    val roleId: String,
    val groups: Map<String, GroupConfig> = emptyMap()
)

/**
 * 配置提供者接口
 * 定义所有配置读取方法的契约
 */
interface ConfigProvider {
    // ===== LLM 配置 =====
    fun getLlmGoogleApiKey(): String
    fun getLlmGoogleBaseUrl(): String
    fun getLlmGoogleModels(): Map<String, String>
    fun getLlmDeepSeekApiKey(): String
    fun getLlmDeepSeekBaseUrl(): String
    fun getLlmDeepSeekModels(): Map<String, String>
    fun getLlmMinimaxApiKey(): String
    fun getLlmMinimaxBaseUrl(): String
    fun getLlmMinimaxModels(): Map<String, String>
    fun getChoiceModel(): String

    // ===== 第三方服务 =====
    fun getEmbeddingApiKey(): String
    fun getEmbeddingProvider(): String
    fun getSearchApiKey(): String
    fun getSearchProvider(): String

    // ===== 代理 =====
    fun getProxyHttp(): String?
    fun getProxySocks(): String?

    // ===== OneBot =====
    fun getOnebotWs(): String
    fun getOnebotToken(): String
    fun getOnebotBots(): Map<String, BotConfig>
    fun getAdmins(botConfigKey: String, groupId: String): List<String>

    // ===== Web =====
    fun getPlaywrightHost(): String

    // ===== 群组 =====
    fun getDebugGroupId(): String?
    fun getEnableGroups(): List<String>
    fun getMessageRedirectMap(): Map<String, String>

    // ===== 插件配置 =====
    fun getPluginConfig(pluginClass: KClass<*>, pluginName: String): Config
}

/**
 * 配置管理器，委托给 ConfigProvider 实现
 * 在应用启动时通过 init() 注入实现
 */
object ConfigHolder {

    private lateinit var provider: ConfigProvider

    fun init(provider: ConfigProvider) {
        this.provider = provider
    }

    // ===== LLM 配置 =====
    fun getLlmGoogleApiKey(): String = provider.getLlmGoogleApiKey()
    fun getLlmGoogleBaseUrl(): String = provider.getLlmGoogleBaseUrl()
    fun getLlmGoogleModels(): Map<String, String> = provider.getLlmGoogleModels()
    fun getLlmDeepSeekApiKey(): String = provider.getLlmDeepSeekApiKey()
    fun getLlmDeepSeekBaseUrl(): String = provider.getLlmDeepSeekBaseUrl()
    fun getLlmDeepSeekModels(): Map<String, String> = provider.getLlmDeepSeekModels()
    fun getLlmMinimaxApiKey(): String = provider.getLlmMinimaxApiKey()
    fun getLlmMinimaxBaseUrl(): String = provider.getLlmMinimaxBaseUrl()
    fun getLlmMinimaxModels(): Map<String, String> = provider.getLlmMinimaxModels()
    fun getChoiceModel(): String = provider.getChoiceModel()

    // ===== 第三方服务 =====
    fun getEmbeddingApiKey(): String = provider.getEmbeddingApiKey()
    fun getEmbeddingProvider(): String = provider.getEmbeddingProvider()
    fun getExaApiKey(): String = provider.getSearchApiKey()
    fun getSearchProvider(): String = provider.getSearchProvider()

    // ===== 代理 =====
    fun getProxyHttp(): String? = provider.getProxyHttp()
    fun getProxySocks(): String? = provider.getProxySocks()

    // ===== OneBot =====
    fun getOnebotWs(): String = provider.getOnebotWs()
    fun getOnebotToken(): String = provider.getOnebotToken()
    fun getOnebotBots(): Map<String, BotConfig> = provider.getOnebotBots()
    fun getAdmins(botConfigKey: String, groupId: String): List<String> = provider.getAdmins(botConfigKey, groupId)

    // ===== Web =====
    fun getPlaywrightHost(): String = provider.getPlaywrightHost()

    // ===== 群组 =====
    fun getDebugGroupId(): String? = provider.getDebugGroupId()
    fun getEnableGroups(): List<String> = provider.getEnableGroups()
    fun getMessageRedirectMap(): Map<String, String> = provider.getMessageRedirectMap()

    // ===== 插件配置 =====
    fun getPluginConfig(pluginClass: KClass<*>, pluginName: String): Config =
        provider.getPluginConfig(pluginClass, pluginName)
}