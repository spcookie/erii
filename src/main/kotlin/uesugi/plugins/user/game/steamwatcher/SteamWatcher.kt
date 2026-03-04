package uesugi.plugins.user.game.steamwatcher

import com.google.auto.service.AutoService
import kotlinx.coroutines.*
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import uesugi.BotManage
import uesugi.core.ProactiveSpeakFeature
import uesugi.core.plugin.*
import uesugi.toolkit.logger

@AutoService(Plugin::class)
class SteamWatcher : PassivePlugin {

    internal val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val log = logger()

    internal data class UserState(
        var personastate: Int,
        var gameid: String?,
        var lastGameId: String? = null,
        var lastUnlockTime: Long = 0L
    )

    private val lastStates = mutableMapOf<String, UserState>()

    // 检测字符串是否包含中文字符
    private fun String.containsChinese(): Boolean {
        return Regex("[\\u4e00-\\u9fa5]").containsMatchIn(this)
    }

    private suspend fun checkUpdates() {
        if (Subscribers.bindings.isEmpty()) return
        log.debug("开始检查 ${Subscribers.bindings.size} 个总绑定...")

        val groupedBindings = Subscribers.bindings.groupBy { it.steamId }
        log.debug("将 ${Subscribers.bindings.size} 个绑定分成了 ${groupedBindings.size} 个独立 Steam 用户进行检查。")

        groupedBindings.forEach { (steamId, userBindings) ->
            // steamId 对应的所有绑定列表
            checkUserAndNotify(steamId, userBindings)
            delay(200)
        }
    }

    private suspend fun checkUserAndNotify(
        steamId: String,
        bindings: List<Subscribers.Subscription>,
        forceNotify: Boolean = false
    ) {
        log.debug("checkUser: 开始检查 steamId=$steamId (涉及 ${bindings.size} 个群组)")
        try {
            // 1. API 请求
            val summary = SteamApi.getPlayerSummary(steamId) ?: return
            var displayGameName = summary.gameextrainfo

            if (SteamWatcherConfig.enableTranslation && summary.gameid != null) {
                val translatedName = SteamApi.getStoreGameName(summary.gameid)
                if (translatedName != null && translatedName.containsChinese()) {
                    displayGameName = translatedName
                }
            }

            // 状态检查
            val newState = UserState(summary.personastate, summary.gameid)
            var currentState = lastStates[steamId]

            if (currentState == null) {
                // 初始化逻辑
                var initialUnlockTime = 0L
                if (summary.gameid != null) {
                    // 必须成功获取一次成就列表作为基准，否则不初始化
                    val achievements = SteamApi.getPlayerAchievements(steamId, summary.gameid)
                    if (achievements == null) {
                        log.warn("checkUser: 初始化延迟 - 无法获取 $steamId 的成就数据，将在下次循环重试。")
                        return
                    }
                    initialUnlockTime = achievements.filter { it.achieved == 1 }.maxOfOrNull { it.unlocktime } ?: 0L
                }

                currentState = newState
                currentState.lastUnlockTime = initialUnlockTime
                lastStates[steamId] = currentState

                if (forceNotify) {
                    bindings.forEach { binding ->
                        sendUpdate(binding.groupId, summary, displayGameName = displayGameName)
                    }
                } else {
                    log.info("记录初始状态：steamId=$steamId (基准时间=$initialUnlockTime)，不发送通知")
                }
                return
            }

            val newIsOnline = newState.personastate > 0
            val currentIsOnline = currentState.personastate > 0

            // 状态变化通知
            if (newIsOnline != currentIsOnline || newState.gameid != currentState.gameid) {
                log.info("检测到重大状态变化：steamId=$steamId")
                currentState.personastate = newState.personastate
                currentState.gameid = newState.gameid

                bindings.forEach { binding ->
                    sendUpdate(binding.groupId, summary, displayGameName = displayGameName)
                }
            }

            // 4. 成就检查
            if (summary.gameid != null) {
                val appId = summary.gameid

                // 游戏切换检测
                if (appId != currentState.lastGameId) {
                    val achievements = SteamApi.getPlayerAchievements(steamId, appId)
                    if (achievements == null) return // 获取失败则下次再试

                    currentState.lastGameId = appId
                    currentState.lastUnlockTime =
                        achievements.filter { it.achieved == 1 }.maxOfOrNull { it.unlocktime } ?: 0L
                    log.debug("checkUser: 游戏切换至 $appId，基准时间重置为 ${currentState.lastUnlockTime}")
                    return // 切换游戏时直接返回，不检测新成就
                }

                val achievements = SteamApi.getPlayerAchievements(steamId, appId) ?: return
                val newAchievements =
                    achievements.filter { it.achieved == 1 && it.unlocktime > currentState.lastUnlockTime }

                if (newAchievements.isNotEmpty()) {
                    val sortedNew = newAchievements.sortedBy { it.unlocktime }

                    // 洪水防御
                    if (sortedNew.size > 5) {
                        log.info("🛡️ 触发洪水防御：检测到 $steamId 同时有 ${sortedNew.size} 个成就变动，判定为历史数据同步，跳过推送。")
                        currentState.lastUnlockTime = sortedNew.maxOf { it.unlocktime } // 仅更新时间
                        return
                    }

                    log.info("检测到新成就：steamId=$steamId，数量=${newAchievements.size}")
                    val schema = SteamApi.getSchemaForGame(appId)
                    val globalPercentages = SteamApi.getGlobalAchievementPercentages(appId)?.associateBy { it.name }

                    if (schema?.game == null) {
                        log.warn("获取游戏 Schema 失败，无法发送成就通知")
                        return
                    }

                    for (ach in sortedNew) {
                        val schemaAch = schema.game.availableGameStats?.achievements?.find { it.name == ach.apiname }
                        if (schemaAch != null) {
                            val info = ImageRenderer.AchievementInfo(
                                name = schemaAch.displayName,
                                description = schemaAch.description,
                                iconUrl = schemaAch.icon,
                                globalUnlockPercentage = globalPercentages?.get(ach.apiname)?.percent ?: 0.0
                            )
                            bindings.forEach { binding ->
                                sendUpdate(binding.groupId, summary, info, displayGameName)
                            }
                            delay(1000)
                        }
                    }
                    currentState.lastUnlockTime = sortedNew.maxOf { it.unlocktime }
                }
            } else {
                currentState.lastGameId = null
                currentState.lastUnlockTime = 0L
            }
        } catch (e: Exception) {
            log.error("获取 Steam 状态失败: steamId=$steamId", e)
        }
    }

    private suspend fun sendUpdate(
        groupId: Long,
        summary: SteamApi.PlayerSummary,
        achievement: ImageRenderer.AchievementInfo? = null,
        displayGameName: String? = null
    ) {
        val isOnline = summary.personastate > 0
        val isPlaying = displayGameName != null
        log.debug("sendUpdate: 准备发送消息... isPlaying=$isPlaying, isOnline=$isOnline, achievement=${achievement != null}")

        //通知
        val shouldNotify = if (achievement != null) {
            //检查成就通知开关
            SteamWatcherConfig.notifyAchievement
        } else {
            // 2. 如果没有成就信息（是普通状态更新），则检查游戏/在线开关
            when {
                isPlaying && SteamWatcherConfig.notifyGame -> true
                !isPlaying && isOnline && SteamWatcherConfig.notifyOnline -> true
                !isOnline && SteamWatcherConfig.notifyOnline -> true
                else -> false
            }
        }
        if (!shouldNotify) return

        try {
            // 将包含翻译名称的 summary 传递给渲染器
            log.debug("sendUpdate: 正在渲染图片...")
            val finalSummary = summary.copy(gameextrainfo = displayGameName)
            val imageBytes = ImageRenderer.render(finalSummary, achievement)

            val roledBot = BotManage.getAllBots().firstOrNull() ?: return
            val bot = roledBot.refBot
            val group = bot.getGroup(groupId) ?: return

            log.debug("sendUpdate: 正在上传图片到群 $groupId...")
            val resource = imageBytes.toExternalResource()
            try {

                // 生成文本
                val text = when {
                    achievement != null -> "${summary.personaname} 在 ${displayGameName ?: "游戏"} 中解锁了成就 ${achievement.name}"
                    isPlaying -> "${summary.personaname} 正在玩 $displayGameName"
                    isOnline -> "${summary.personaname} 当前状态 在线"
                    else -> "${summary.personaname} 当前状态 离线"
                }

                val img: Image = group.uploadImage(resource)
                val message = MessageChainBuilder().append(text).append("\n").append(img).build()

                sendAgent(
                    bot.id.toString(),
                    group.id.toString(),
                    "你一直在观察群友的 Steam 状态，发现 ${summary.personaname} 的 Steam 状态已更新，${text}，请告知群友",
                    SendAgentConf(
                        flag = ProactiveSpeakFeature.GRAB or ProactiveSpeakFeature.FALLBACK
                    )
                ) {
                    sendAfter {
                        this@SteamWatcher.scope.launch {
                            group.sendMessage(message)
                        }
                    }

                    dispatchFallback {
                        this@SteamWatcher.scope.launch {
                            group.sendMessage(message)
                        }
                    }
                    this@SteamWatcher.scope
                }

            } finally {
                withContext(Dispatchers.IO) { resource.close() }
            }
        } catch (e: Exception) {
            log.error("发送更新失败 (group=$groupId, steam=${summary.steamid}) -> ${e.message}")
        }
    }

    override fun onLoad(context: PluginContext) {
        if (SteamWatcherConfig.apiKey.isNullOrBlank()) {
            log.info("⚠️ Steam API Key 未设置，插件无法正常工作！")
        }
        log.info("✅ SteamWatcher 插件已启用")

        scope.launch {
            while (isActive) {
                try {
                    checkUpdates()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log.error("检查更新主循环出错", e)
                }
                delay(SteamWatcherConfig.interval)
            }
        }
    }

    override fun onUnload() {
        scope.cancel()
    }
}