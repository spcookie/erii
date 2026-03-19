package uesugi.core.state.volition

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.*
import org.jobrunr.scheduling.JobScheduler
import org.koin.core.context.GlobalContext
import uesugi.BotManage
import uesugi.common.*
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

class VolitionJob(
    val jobScheduler: JobScheduler,
    private val volitionAgent: VolitionAgent,
    private val volitionRepository: VolitionRepository
) {

    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()

    private val scope = CoroutineScope(Dispatchers.Default)

    fun openTimingTriggerSignal() {
        for (group in ENABLE_GROUPS) {
            for (bot in BotManage.getAllBotIds()) {
                log.info("init volition for bot $bot in group $group")
                ensureVolitionGaugeExists(bot, group)
            }
        }
        jobScheduler.scheduleRecurrently(
            "volition-job",
            "*/2 * * * *",
            ::doVolitionAnalysis
        )
        log.info("主动意愿任务定时器已启动, 执行周期: 每2分钟")

        startDailyTasks()
        startSilentMonitor()
    }

    @OptIn(ExperimentalTime::class)
    fun doVolitionAnalysis() {
        runBlocking {
            if (mutex.tryLock()) {
                try {
                    log.debug("主动意愿任务开始执行")
                    for (currentBotId in BotManage.getAllBotIds()) {
                        log.debug("开始处理主动意愿: currentBotId=$currentBotId")

                        val groups = withContext(Dispatchers.IO) {
                            volitionRepository.findGroupsNeedProcessing(currentBotId)
                        }

                        log.debug("主动意愿任务发现 ${groups.size} 个群组需要处理")

                        for (groupId in groups) {
                            ensureVolitionGaugeExists(currentBotId, groupId)
                            processGroupVolition(currentBotId, groupId)
                        }
                    }

                    log.debug("主动意愿任务执行完成")
                } catch (e: Exception) {
                    log.error("主动意愿任务执行失败", e)
                } finally {
                    mutex.unlock()
                }
            } else {
                log.debug("主动意愿任务正在执行中, 跳过本次调度")
            }
        }
    }

    private fun ensureVolitionGaugeExists(botMark: String, groupId: String) {
        val volitionGaugeManager = GlobalContext.get().get<VolitionGaugeManager>()
        volitionGaugeManager.getOrCreate(botMark, groupId, BotManage.getBot(botMark).role.emoticon)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun processGroupVolition(botMark: String, groupId: String) {
        log.debug("开始处理群组主动意愿, groupId=$groupId")

        try {
            val volitionState = volitionRepository.getVolitionState(botMark, groupId)
            val lastId = volitionState?.lastProcessedHistoryId ?: 0

            val histories = withContext(Dispatchers.IO) {
                volitionRepository.getHistoriesToProcess(botMark, groupId, lastId, 100)
            }

            if (histories.size < 20) {
                log.debug("群组 $groupId 新消息不足 20 条, 跳过处理主动意愿分析")
                return
            }

            log.debug("群组 $groupId 获取到 ${histories.size} 条新消息")

            val messages = histories.map {
                VolitionMessage(
                    id = it.id.value,
                    botId = botMark,
                    groupId = it.groupId,
                    userId = it.userId,
                    time = it.createdAt,
                    content = it.content ?: ""
                )
            }

            if (messages.isEmpty()) {
                log.debug("群组 $groupId 消息转换后为空, 跳过处理")
                val maxHistoryId = histories.maxOf { it.id.value }
                volitionRepository.updateVolitionState(botMark, groupId, maxHistoryId)
                return
            }

            analyze(botMark, groupId, messages)

            val maxHistoryId = histories.maxOf { it.id.value }
            volitionRepository.updateVolitionState(botMark, groupId, maxHistoryId)

            log.debug("群组 $groupId 主动意愿处理完成, 最大 historyId=$maxHistoryId")

        } catch (e: Exception) {
            log.error("处理群组 $groupId 主动意愿失败", e)
        }
    }

    private suspend fun analyze(
        botMark: String,
        groupId: String,
        messages: List<VolitionMessage>
    ) {
        val volitionGaugeManager = GlobalContext.get().get<VolitionGaugeManager>()
        val gauge = volitionGaugeManager.get(botMark, groupId) ?: return

        val botInterests = BotManage.getBot(botMark).role.character

        val result = volitionAgent.analysis(messages, botInterests, gauge.getMood()) ?: return

        log.info("冲动值分析完成, botId=$botMark, groupId=$groupId, $result")

        EventBus.postAsync(ResetStimulusEvent(botMark, groupId))

        if (result.keywordHit) {
            EventBus.postAsync(KeywordHitEvent(botMark, groupId, result.keywordStrength * 30))
        }

        if (result.isBusy) {
            EventBus.postAsync(BusyGroupEvent(botMark, groupId))
        }

        if (result.indirectMention) {
            EventBus.postAsync(IndirectMentionEvent(botMark, groupId))
        }

        if (result.emotionalResonance) {
            EventBus.postAsync(EmotionalResonanceEvent(botMark, groupId))
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun startDailyTasks() {
        scope.launch {
            launchJitterDailyTask(
                baseHour = 15,
                baseMinute = 0,
                minOffsetMinutes = 0,
                maxOffsetMinutes = 30
            ) { triggerDailySpeak() }

            launchJitterDailyTask(
                baseHour = 20,
                baseMinute = 0,
                minOffsetMinutes = 0,
                maxOffsetMinutes = 30
            ) { triggerDailySpeak() }
        }
    }

    private fun triggerDailySpeak() {
        val volitionGaugeManager = GlobalContext.get().get<VolitionGaugeManager>()
        volitionGaugeManager.getAllGauges().forEach { (key, _) ->
            val (botMark, groupId) = key.split(":")
            if (ENABLE_GROUPS.contains(groupId)) {
                val groupId =
                    if (groupId in MESSAGE_REDIRECT_GROUP_MAP) {
                        MESSAGE_REDIRECT_GROUP_MAP.getValue(groupId)
                    } else {
                        groupId
                    }
                log.info("决策: 群组 $groupId 定时发言")
                speakV(
                    botId = botMark,
                    groupId = groupId,
                    interruptionMode = InterruptionMode.Routine
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun startSilentMonitor() {
        val volitionGaugeManager = GlobalContext.get().get<VolitionGaugeManager>()
        scope.launch {
            while (isActive) {
                delay(10.minutes)

                val now = System.currentTimeMillis()

                volitionGaugeManager.getAllGauges().forEach { (key, gauge) ->
                    val (botMark, groupId) = key.split(":")
                    if (ENABLE_GROUPS.contains(groupId)) {
                        val groupId =
                            if (groupId in MESSAGE_REDIRECT_GROUP_MAP) {
                                MESSAGE_REDIRECT_GROUP_MAP.getValue(groupId)
                            } else {
                                groupId
                            }
                        if (now - gauge.state.lastActiveTime > 4.hours.inWholeMilliseconds) {
                            gauge.state.lastActiveTime = now

                            val dateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                            val hour = dateTime.hour
                            val inRange = hour in 8 until 22
                            if (inRange) {
                                log.info("决策: 群组 $groupId 4 小时内无消息，主动发言")
                                speakV(
                                    botId = botMark,
                                    groupId = groupId
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun CoroutineScope.launchJitterDailyTask(
        baseHour: Int,
        baseMinute: Int,
        minOffsetMinutes: Int,
        maxOffsetMinutes: Int,
        task: suspend () -> Unit
    ) = launch {
        while (isActive) {
            val zone = TimeZone.currentSystemDefault()
            val now = Clock.System.now()

            val today = now.toLocalDateTime(zone).date

            val baseTime = LocalDateTime(
                date = today,
                time = LocalTime(baseHour, baseMinute)
            )

            val offset = Random.nextInt(
                minOffsetMinutes,
                maxOffsetMinutes + 1
            )

            val triggerTime = baseTime
                .toInstant(zone)
                .plus(offset.minutes)

            val delayMillis = triggerTime - now

            if (delayMillis.isPositive()) {
                delay(delayMillis)
                task()
            }

            delay(1.minutes)
        }
    }
}