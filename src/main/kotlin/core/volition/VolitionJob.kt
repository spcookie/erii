package uesugi.core.volition

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.*
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.scheduling.BackgroundJob
import org.koin.core.context.GlobalContext
import uesugi.BotManage
import uesugi.core.InterruptionMode
import uesugi.core.ProactiveSpeakEvent
import uesugi.core.history.HistoryEntity
import uesugi.core.history.HistoryTable
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

class VolitionJob {

    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()
    private val volitionAgent = VolitionAgent()

    private val scope = CoroutineScope(Dispatchers.Default)

    fun openTimingTriggerSignal() {
        BackgroundJob.scheduleRecurrently(
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
                            transaction {
                                findGroupsNeedProcessing(currentBotId)
                            }
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
        volitionGaugeManager.getOrCreate(botMark, groupId, BotManage.getBot(botMark)!!.role.emoticon)
    }

    private fun findGroupsNeedProcessing(botMark: String): List<String> {
        return transaction {
            val allGroupIds = HistoryTable
                .select(HistoryTable.groupId)
                .where { HistoryTable.botMark eq botMark }
                .groupBy(HistoryTable.groupId)
                .map { it[HistoryTable.groupId] }
                .distinct()

            allGroupIds.filter { groupId ->
                val volitionState = VolitionStateEntity.find {
                    (VolitionStateTable.botMark eq botMark) and (VolitionStateTable.groupId eq groupId)
                }.firstOrNull()

                val lastProcessedId = volitionState?.lastProcessedHistoryId ?: 0

                val newMessageCount = HistoryEntity.count(
                    HistoryTable.botMark eq botMark and
                            (HistoryTable.groupId eq groupId) and
                            (HistoryTable.id greater lastProcessedId)
                )

                newMessageCount > 0
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun processGroupVolition(botMark: String, groupId: String) {
        log.debug("开始处理群组主动意愿, groupId=$groupId")

        try {
            val histories = withContext(Dispatchers.IO) {
                transaction {
                    val volitionState = VolitionStateEntity.find {
                        (VolitionStateTable.botMark eq botMark) and (VolitionStateTable.groupId eq groupId)
                    }.firstOrNull()

                    val lastId = volitionState?.lastProcessedHistoryId ?: 0

                    val historyList = HistoryEntity.find {
                        (HistoryTable.botMark eq botMark) and
                                (HistoryTable.groupId eq groupId) and
                                (HistoryTable.id greater lastId)
                    }
                        .orderBy(HistoryTable.createdAt to SortOrder.ASC)
                        .limit(50)
                        .toList()

                    historyList
                }
            }

            if (histories.isEmpty()) {
                log.debug("群组 $groupId 没有新消息需要处理")
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
                log.debug("群组 $groupId 消息转换后为空,跳过处理")
                updateVolitionState(botMark, groupId, histories.maxOf { it.id.value })
                return
            }

            analyzeAndDecide(botMark, groupId, messages)

            val maxHistoryId = histories.maxOf { it.id.value }
            updateVolitionState(botMark, groupId, maxHistoryId)

            log.debug("群组 $groupId 主动意愿处理完成, 最大 historyId=$maxHistoryId")

        } catch (e: Exception) {
            log.error("处理群组 $groupId 主动意愿失败", e)
        }
    }

    private suspend fun analyzeAndDecide(
        botMark: String,
        groupId: String,
        messages: List<VolitionMessage>
    ) {
        val volitionGaugeManager = GlobalContext.get().get<VolitionGaugeManager>()
        val gauge = volitionGaugeManager.get(botMark, groupId) ?: return

        val impulse = gauge.calculateImpulse()

        val botInterests = BotManage.getBot(botMark)!!.role.character

        val result = volitionAgent.analysis(messages, botInterests, gauge.getMood()) ?: return

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

        if (gauge.shouldSpeak()) {
            log.info("决策: 群组 $groupId 应该主动发言!")

            EventBus.postAsync(
                ProactiveSpeakEvent(
                    botId = botMark,
                    _groupId = groupId,
                    impulse = impulse,
                    interruptionMode = InterruptionMode.Interrupt,
                )
            )

            gauge.addFatigue(100.0)
            gauge.state.lastActiveTime = System.currentTimeMillis()
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun updateVolitionState(botMark: String, groupId: String, lastHistoryId: Int) {
        withContext(Dispatchers.IO) {
            transaction {
                val existing = VolitionStateEntity.find {
                    (VolitionStateTable.botMark eq botMark) and (VolitionStateTable.groupId eq groupId)
                }.firstOrNull()

                val now = Clock.System.now()
                val tz = TimeZone.currentSystemDefault()
                val instant = now.toLocalDateTime(tz)

                if (existing != null) {
                    existing.lastProcessedHistoryId = lastHistoryId
                    existing.lastProcessedAt = instant
                } else {
                    VolitionStateEntity.new {
                        this.botMark = botMark
                        this.groupId = groupId
                        this.lastProcessedHistoryId = lastHistoryId
                        this.lastProcessedAt = instant
                    }
                }

                log.debug("主动意愿状态已更新, groupId=$groupId, lastHistoryId=$lastHistoryId")
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun startDailyTasks() {
        scope.launch {
            launchJitterDailyTask(
                baseHour = 8,
                baseMinute = 0,
                minOffsetMinutes = 0,
                maxOffsetMinutes = 60
            ) {
                triggerDailySpeak(InterruptionMode.Routine)
            }

            launchJitterDailyTask(
                baseHour = 12,
                baseMinute = 30,
                minOffsetMinutes = 0,
                maxOffsetMinutes = 30
            ) {
                triggerDailySpeak(InterruptionMode.Icebreak)
            }

            launchJitterDailyTask(
                baseHour = 18,
                baseMinute = 0,
                minOffsetMinutes = 0,
                maxOffsetMinutes = 30
            ) {
                triggerDailySpeak(InterruptionMode.Routine)
            }

            launchJitterDailyTask(
                baseHour = 22,
                baseMinute = 0,
                minOffsetMinutes = 0,
                maxOffsetMinutes = 30
            ) {
                triggerDailySpeak(InterruptionMode.Routine)
            }
        }
    }

    private fun triggerDailySpeak(mode: InterruptionMode) {
        val volitionGaugeManager = GlobalContext.get().get<VolitionGaugeManager>()
        volitionGaugeManager.getAllGauges().forEach { (key, gauge) ->
            val (botMark, groupId) = key.split(":")
            EventBus.postAsync(
                ProactiveSpeakEvent(
                    botId = botMark,
                    _groupId = groupId,
                    impulse = gauge.calculateImpulse(),
                    interruptionMode = mode
                )
            )
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
                    if (now - gauge.state.lastActiveTime > 4.hours.inWholeMilliseconds) {
                        gauge.state.lastActiveTime = now

                        val dateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                        val hour = dateTime.hour
                        val inRange = hour in 8 until 22
                        if (inRange) {
                            EventBus.postAsync(
                                ProactiveSpeakEvent(
                                    botId = botMark,
                                    _groupId = groupId,
                                    impulse = gauge.calculateImpulse(),
                                    interruptionMode = InterruptionMode.Icebreak
                                )
                            )
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