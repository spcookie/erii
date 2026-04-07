package uesugi.common

import com.typesafe.config.Config
import kotlin.reflect.KClass

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
