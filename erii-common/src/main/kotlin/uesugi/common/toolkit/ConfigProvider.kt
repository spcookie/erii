package uesugi.common.toolkit

import com.typesafe.config.Config
import kotlin.reflect.KClass

/**
 * 群组配置数据类
 */
data class GroupConfig(
    val admins: List<String>,
    val desire: Double
)

/**
 * Bot 维度的群组配置覆盖（可选），未指定字段时回退到全局 groups 配置
 */
data class BotGroupsOverride(
    val enableGroups: List<String>? = null,
    val debugGroupId: String? = null,
    val messageRedirectMap: Map<String, String>? = null
)

/**
 * Bot 配置数据类
 */
data class BotConfig(
    val ws: String,
    val token: String,
    val roleId: String,
    val selfId: String? = null,
    val groups: Map<String, GroupConfig> = emptyMap(),
    val groupsOverride: BotGroupsOverride? = null,
    val enabledPlugins: List<String>? = null,
    val disabledPlugins: List<String>? = null,
    val externalHost: String? = null
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
    fun getLlmOpenAIApiKey(): String
    fun getLlmOpenAIBaseUrl(): String
    fun getLlmOpenAIModels(): Map<String, String>
    fun getLlmAnthropicApiKey(): String
    fun getLlmAnthropicBaseUrl(): String
    fun getLlmAnthropicModels(): Map<String, String>
    fun getLlmOpenRouterApiKey(): String
    fun getLlmOpenRouterBaseUrl(): String
    fun getLlmOpenRouterModels(): Map<String, String>
    fun getChoiceProvider(): String
    fun isLlmCapabilityEnabled(name: String): Boolean
    fun isLlmCapabilityEnabled(tier: String, name: String): Boolean

    // ===== 第三方服务 =====
    fun getEmbeddingApiKey(): String
    fun getEmbeddingProvider(): String
    fun getEmbeddingUrl(): String
    fun getEmbeddingModel(): String
    fun getSearchApiKey(): String
    fun getSearchProvider(): String
    fun getSearchUrl(): String
    fun getVisionApiKey(): String
    fun getVisionProvider(): String
    fun getVisionUrl(): String

    // ===== 代理 =====
    fun getProxyHttp(): String?
    fun getProxySocks(): String?

    // ===== OneBot =====
    fun getOnebotWs(): String
    fun getOnebotToken(): String
    fun getOnebotBots(): Map<String, BotConfig>
    fun getAdmins(botConfigKey: String, groupId: String): List<String>

    // ===== Web =====
    fun getPlaywrightUrl(): String
    fun getBrowserDownload(): Boolean
    fun getBrowserExternalHost(): String

    // ===== 群组 =====
    fun getDebugGroupId(): String?
    fun getEnableGroups(): List<String>
    fun getMessageRedirectMap(): Map<String, String>

    // ===== 群组（Bot 维度有效值，含覆盖逻辑）=====
    fun getEffectiveEnableGroups(botKey: String): List<String>
    fun getEffectiveDebugGroupId(botKey: String): String?
    fun getEffectiveMessageRedirectMap(botKey: String): Map<String, String>

    // ===== 插件配置 =====
    fun getPluginConfig(pluginClass: KClass<*>, pluginName: String): Config

    // ===== 插件启用/禁用（Bot 维度）=====
    fun getEnabledPlugins(botKey: String): List<String>?
    fun getDisabledPlugins(botKey: String): List<String>?
    fun isPluginEnabled(botKey: String, pluginName: String): Boolean

    // ===== 刷新缓存 =====
    fun refresh()
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
    fun getLlmOpenAIApiKey(): String = provider.getLlmOpenAIApiKey()
    fun getLlmOpenAIBaseUrl(): String = provider.getLlmOpenAIBaseUrl()
    fun getLlmOpenAIModels(): Map<String, String> = provider.getLlmOpenAIModels()
    fun getLlmAnthropicApiKey(): String = provider.getLlmAnthropicApiKey()
    fun getLlmAnthropicBaseUrl(): String = provider.getLlmAnthropicBaseUrl()
    fun getLlmAnthropicModels(): Map<String, String> = provider.getLlmAnthropicModels()
    fun getLlmOpenRouterApiKey(): String = provider.getLlmOpenRouterApiKey()
    fun getLlmOpenRouterBaseUrl(): String = provider.getLlmOpenRouterBaseUrl()
    fun getLlmOpenRouterModels(): Map<String, String> = provider.getLlmOpenRouterModels()
    fun getChoiceProvider(): String = provider.getChoiceProvider()
    fun isLlmCapabilityEnabled(name: String): Boolean = provider.isLlmCapabilityEnabled(name)
    fun isLlmCapabilityEnabled(tier: String, name: String): Boolean = provider.isLlmCapabilityEnabled(tier, name)

    // ===== 第三方服务 =====
    fun getEmbeddingApiKey(): String = provider.getEmbeddingApiKey()
    fun getEmbeddingProvider(): String = provider.getEmbeddingProvider()
    fun getEmbeddingUrl(): String = provider.getEmbeddingUrl()
    fun getEmbeddingModel(): String = provider.getEmbeddingModel()
    fun getSearchApiKey(): String = provider.getSearchApiKey()
    fun getSearchProvider(): String = provider.getSearchProvider()
    fun getSearchUrl(): String = provider.getSearchUrl()
    fun getVisionApiKey(): String = provider.getVisionApiKey()
    fun getVisionProvider(): String = provider.getVisionProvider()
    fun getVisionUrl(): String = provider.getVisionUrl()

    // ===== 代理 =====
    fun getProxyHttp(): String? = provider.getProxyHttp()
    fun getProxySocks(): String? = provider.getProxySocks()

    // ===== OneBot =====
    fun getOnebotWs(): String = provider.getOnebotWs()
    fun getOnebotToken(): String = provider.getOnebotToken()
    fun getOnebotBots(): Map<String, BotConfig> = provider.getOnebotBots()
    fun getAdmins(botConfigKey: String, groupId: String): List<String> = provider.getAdmins(botConfigKey, groupId)

    // ===== Web =====
    fun getPlaywrightUrl(): String = provider.getPlaywrightUrl()
    fun getBrowserDownload(): Boolean = provider.getBrowserDownload()
    fun getBrowserExternalHost(): String = provider.getBrowserExternalHost()

    // ===== 群组 =====
    fun getDebugGroupId(): String? = provider.getDebugGroupId()
    fun getEnableGroups(): List<String> = provider.getEnableGroups()
    fun getMessageRedirectMap(): Map<String, String> = provider.getMessageRedirectMap()

    // ===== 群组（Bot 维度有效值）=====
    fun getEffectiveEnableGroups(botKey: String): List<String> = provider.getEffectiveEnableGroups(botKey)
    fun getEffectiveDebugGroupId(botKey: String): String? = provider.getEffectiveDebugGroupId(botKey)
    fun getEffectiveMessageRedirectMap(botKey: String): Map<String, String> =
        provider.getEffectiveMessageRedirectMap(botKey)

    // ===== 插件配置 =====
    fun getPluginConfig(pluginClass: KClass<*>, pluginName: String): Config =
        provider.getPluginConfig(pluginClass, pluginName)

    // ===== 插件启用/禁用（Bot 维度）=====
    fun getEnabledPlugins(botKey: String): List<String>? = provider.getEnabledPlugins(botKey)
    fun getDisabledPlugins(botKey: String): List<String>? = provider.getDisabledPlugins(botKey)
    fun isPluginEnabled(botKey: String, pluginName: String): Boolean = provider.isPluginEnabled(botKey, pluginName)

    // ===== 刷新缓存 =====
    fun refresh() = provider.refresh()
}