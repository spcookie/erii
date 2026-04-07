package uesugi.common

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
