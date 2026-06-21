package uesugi.core.state.emotion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.jobrunr.scheduling.JobScheduler
import uesugi.common.BotManage
import uesugi.common.data.HistoryRecord
import uesugi.common.data.MessageType
import uesugi.common.toolkit.logger
import uesugi.core.component.usage.UsageContext
import uesugi.core.state.dispatch.*

/**
 * 情绪任务 - 事件驱动情绪分析与定时衰减
 *
 * 仅负责调度和组合逻辑，业务逻辑封装在 EmotionService 中
 */
class EmotionJob(
    val jobScheduler: JobScheduler,
    private val emotionRepository: EmotionRepository,
    private val emotionService: EmotionService
) : StateWorkProcessor {

    override val kind = StateWorkKind.EMOTION

    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()

    /**
     * 开启每 2 分钟执行一次的情绪衰减。
     */
    fun openTimingTriggerSignal() {
        jobScheduler.scheduleRecurrently(
            "emotion-decay-job",
            "*/2 * * * *",
            ::doDecay
        )
        log.info("Emotion decay timer started, execution cycle: every 2 minutes")
    }

    override fun accepts(record: HistoryRecord): Boolean = record.messageType == MessageType.TEXT

    override fun pendingKeys(): Set<StateWorkKey> = buildSet {
        for (botId in BotManage.getAllBotIds()) {
            emotionRepository.findGroupsNeedAnalysis(botId).forEach { groupId ->
                add(StateWorkKey(botId, groupId, kind))
            }
        }
    }

    override suspend fun process(
        key: StateWorkKey,
        policy: StateWorkPolicy,
        force: Boolean
    ): StateWorkResult = UsageContext.withUsage(key.botId, key.groupId) {
        val cursor = emotionService.analyzeGroupEmotion(
            currentBotId = key.botId,
            groupId = key.groupId,
            batchLimit = policy.batchLimit,
            minimumMessages = policy.minMessages,
            force = force
        )
        StateWorkResult(if (cursor == null) 0 else 1, cursor, hasMore = false)
    }

    fun doDecay() {
        if (!mutex.tryLock()) return
        try {
            runBlocking {
                for (botId in BotManage.getAllBotIds()) {
                    val groups = withContext(Dispatchers.IO) {
                        emotionRepository.findGroupsNotNeedAnalysis(botId, emptyList())
                    }
                    groups.forEach { groupId ->
                        runCatching { emotionService.decayGroupEmotion(botId, groupId) }
                            .onFailure { log.error("Emotion decay failed, botId=$botId, groupId=$groupId", it) }
                    }
                }
            }
        } finally {
            mutex.unlock()
        }
    }

}
