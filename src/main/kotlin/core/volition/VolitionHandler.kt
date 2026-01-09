package uesugi.core.volition

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.*
import uesugi.core.history.HistorySavedEvent
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

class VolitionHandler(
    private val volitionGauge: VolitionGauge
) {
//    private val volitionGauges = ConcurrentHashMap<String, ConcurrentHashMap<String, VolitionGauge>>()

    companion object {
        private val log = logger()
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    @OptIn(ExperimentalTime::class)
    fun start() {
        val volitionAgent = VolitionAgent(
            """
                兴趣爱好：
                小说、轻小说、动漫、二次元游戏
                文学、哲学思考、历史趣闻
                科技资讯、群聊段子
                
                常聊话题：
                剧情分析或讨论
                群友趣事、吐槽
                网络梗、段子、轻幽默
                偶尔哲理或人生感悟
            """.trimIndent(),
            listOf("游戏", "动漫", "小说", "哲学", "段子")
        )

        val channel = Channel<VolitionMessage>(1000, BufferOverflow.DROP_OLDEST)

        var silentStartTime = Clock.System.now()

        scope.launch {
            val result = mutableListOf<VolitionMessage>()
            while (true) {
                delay(2.minutes)
                while (true) {
                    val r = channel.tryReceive()
                    if (r.isSuccess) {
                        silentStartTime = Clock.System.now()
                        result += r.getOrThrow()
                        if (result.size > 50) {
                            break
                        }
                    } else {
                        break
                    }
                }
                if (result.isNotEmpty()) {
                    analyzeAndDecide(volitionAgent, result)
                    result.clear()
                }
            }
        }

        scope.launch {
            while (true) {
                delay(10.minutes)
                if (Clock.System.now() - silentStartTime > 4.hours) {
                    silentStartTime = Clock.System.now()

                    val dateTime = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    val hour = dateTime.hour
                    val inRange = hour in 8 until 22
                    if (inRange) {
                        EventBus.postAsync(
                            ProactiveSpeakEvent(
                                impulse = volitionGauge.calculateImpulse(),
                                interruptionMode = InterruptionMode.Interrupt
                            )
                        )
                    }
                }
            }
        }

        EventBus.subscribeAsync<HistorySavedEvent>(scope) { event ->
            val historyEntity = event.historyEntity
            val message = VolitionMessage(
                id = historyEntity.id.value,
                historyEntity.botMark,
                historyEntity.groupId,
                userId = historyEntity.userId,
                time = historyEntity.createdAt,
                content = historyEntity.content ?: ""
            )
            channel.send(message)
        }

        scope.launch(Dispatchers.Default) {
            launchJitterDailyTask(
                baseHour = 8,
                baseMinute = 0,
                minOffsetMinutes = 0,
                maxOffsetMinutes = 60
            ) {
                EventBus.postAsync(
                    ProactiveSpeakEvent(
                        impulse = volitionGauge.calculateImpulse(),
                        interruptionMode = InterruptionMode.Routine
                    )
                )
            }

            launchJitterDailyTask(
                baseHour = 12,
                baseMinute = 30,
                minOffsetMinutes = 0,
                maxOffsetMinutes = 30
            ) {
                EventBus.postAsync(
                    ProactiveSpeakEvent(
                        impulse = volitionGauge.calculateImpulse(),
                        interruptionMode = InterruptionMode.Icebreak
                    )
                )
            }

            launchJitterDailyTask(
                baseHour = 18,
                baseMinute = 0,
                minOffsetMinutes = 0,
                maxOffsetMinutes = 30
            ) {
                EventBus.postAsync(
                    ProactiveSpeakEvent(
                        impulse = volitionGauge.calculateImpulse(),
                        interruptionMode = InterruptionMode.Routine
                    )
                )
            }

            launchJitterDailyTask(
                baseHour = 22,
                baseMinute = 0,
                minOffsetMinutes = 0,
                maxOffsetMinutes = 30
            ) {
                EventBus.postAsync(
                    ProactiveSpeakEvent(
                        impulse = volitionGauge.calculateImpulse(),
                        interruptionMode = InterruptionMode.Routine
                    )
                )
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
            }

            task()

            // 防止同一天重复执行
            delay(1.minutes)
        }
    }


    private suspend fun analyzeAndDecide(
        agent: VolitionAgent,
        messages: List<VolitionMessage>
    ) {
        val impulse = volitionGauge.calculateImpulse()

        val result = agent.analysis(messages, volitionGauge.getMood()) ?: return

        EventBus.postAsync(ResetStimulusEvent())

        if (result.keywordHit) {
            EventBus.postAsync(KeywordHitEvent(result.keywordStrength * 30))
        }

        if (result.isBusy) {
            EventBus.postAsync(BusyGroupEvent())
        }

        if (result.indirectMention) {
            EventBus.postAsync(IndirectMentionEvent())
        }

        if (result.emotionalResonance) {
            EventBus.postAsync(EmotionalResonanceEvent())
        }

        if (volitionGauge.shouldSpeak()) {
            log.info("决策: 应该主动发言!")

            EventBus.postAsync(
                ProactiveSpeakEvent(
                    impulse = impulse,
                    interruptionMode = InterruptionMode.Interrupt
                )
            )

            volitionGauge.addFatigue(100.0)
            volitionGauge.state.lastActiveTime = System.currentTimeMillis()
        }
    }

    fun close() {
        try {
            volitionGauge.stop()
            scope.cancel()
        } catch (e: Exception) {
            log.warn("Error closing VolitionHandler", e)
        }
    }
}
