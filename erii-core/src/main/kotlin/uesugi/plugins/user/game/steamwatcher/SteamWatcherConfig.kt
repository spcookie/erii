package uesugi.plugins.user.game.steamwatcher


import uesugi.config.ConfigHolder

object SteamWatcherConfig {
    // Steam API Key
    var apiKey: String? =
        ConfigHolder.getPluginConfig(SteamWatcherConfig::class.java, "SteamWatcher").getString("api-key")
            .takeIf { it.isNotBlank() }

    // 状态检查间隔 (毫秒), 修改后需重载插件
    var interval: Long = 60000L

    // 是否开启在线状态通知
    var notifyOnline: Boolean = true

    // 是否开启游戏状态通知
    var notifyGame: Boolean = true

    // 是否开启成就解锁通知
    var notifyAchievement: Boolean = true

    // 是否启用翻译功能 (请求中文名称)
    var enableTranslation: Boolean = true

    // 请求的语言 (schinese = 简体中文, tchinese = 繁体中文, english = 英语)
    var language: String = "schinese"

}