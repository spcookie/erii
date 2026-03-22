package uesugi.common


sealed interface BotRole {
    val id: String
    val name: String
    fun personality(botId: String): String
    val character: String
    val emoticon: EmotionalTendencies
}

/**
 * 从配置文件加载的 BotRole 实现类
 */
data class ConfigBotRole(
    override val id: String,
    override val name: String,
    private val personalityTemplate: String,
    override val character: String,
    override val emoticon: EmotionalTendencies
) : BotRole {
    override fun personality(botId: String): String = personalityTemplate.replace("{{botId}}", botId)
}