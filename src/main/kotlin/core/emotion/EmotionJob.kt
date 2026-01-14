package uesugi.core.emotion

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.scheduling.BackgroundJob
import uesugi.BotManage
import uesugi.core.history.HistoryEntity
import uesugi.core.history.HistoryTable
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import kotlin.math.exp
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class EmotionJob {

    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()

    /**
     * 开启定时触发
     * 每分钟执行一次情绪分析
     */
    fun openTimingTriggerSignal() {
        BackgroundJob.scheduleRecurrently(
            "emotion-job",
            "* * * * *",  // 每分钟
            ::doAnalysis
        )
        log.info("情绪任务定时器已启动, 执行周期: 每分钟")
    }

    /**
     * 执行情绪分析
     */
    fun doAnalysis() {
        runBlocking {
            if (mutex.tryLock()) {
                try {
                    log.debug("情绪任务开始执行")
                    for (currentBotId in BotManage.getAllBotIds()) {
                        log.debug("开始执行的情绪分析, botId=$currentBotId")

                        // 1. 查找需要分析的群组(有新消息的群组)
                        val groups = withContext(Dispatchers.IO) {
                            transaction {
                                EmotionEntity.findRequiredAnalysisHistoryGroupIds(currentBotId)
                            }
                        }
                        log.debug("情绪任务发现 ${groups.size} 个群组有新消息需要分析")

                        // 2. 对每个群组执行情绪分析
                        for (group in groups) {
                            emotionAnalysis(
                                currentBotId,
                                group,
                                groupSize = 10,
                                adminPresent = false
                            )
                        }

                        // 3. 查找需要衰减的群组(无新消息的群组)
                        val decayGroups = withContext(Dispatchers.IO) {
                            transaction {
                                EmotionEntity.findNotAnalysisHistoryGroupIds(currentBotId, groups)
                            }
                        }
                        log.debug("情绪任务发现 ${decayGroups.size} 个群组需要执行情绪衰减")

                        // 4. 对每个群组执行情绪衰减
                        for (group in decayGroups) {
                            decayEmotion(
                                currentBotId,
                                group,
                                groupSize = 10,
                                adminPresent = false
                            )
                        }
                    }

                    log.debug("情绪任务执行完成")
                } catch (e: Exception) {
                    log.error("情绪任务执行失败", e)
                } finally {
                    mutex.unlock()
                }
            } else {
                log.debug("情绪任务正在执行中, 跳过本次调度")
            }
        }
    }

    /**
     * 情绪衰减处理
     *
     * 当群组没有新消息时,机器人的情绪会随时间自然衰减
     *
     * @param currentBotId 当前机器人ID
     * @param groupId 群组ID
     * @param groupSize 群组人数
     * @param adminPresent 是否有管理员在线
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun decayEmotion(
        currentBotId: String,
        groupId: String,
        groupSize: Int,
        adminPresent: Boolean
    ) {
        withContext(Dispatchers.IO) {
            transaction {
                log.debug("开始执行情绪衰减, groupId=$groupId")

                // 获取当前最新的情绪状态
                val currentEmotionEntity = EmotionEntity.find {
                    EmotionTable.groupId eq groupId and
                            (EmotionTable.botMark eq currentBotId)
                }
                    .orderBy(EmotionTable.createdAt to SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()

                if (currentEmotionEntity == null) {
                    log.debug("群组 $groupId 没有情绪记录, 跳过衰减")
                    return@transaction
                }

                // 计算时间差(秒)
                val tz = TimeZone.currentSystemDefault()
                val now = Clock.System.now()
                val instant = currentEmotionEntity.updatedAt.toInstant(tz)
                val seconds = (now - instant).inWholeSeconds.coerceAtLeast(0)

                log.debug("群组 $groupId 距离上次更新已过 $seconds 秒")

                /**
                 * 情绪衰减函数
                 * 使用指数衰减: emotion × e^(-λ × Δt)
                 */
                fun decayEmotion(
                    emotion: PAD,
                    deltaSeconds: Long
                ): PAD {
                    val lambda = 0.0001  // 衰减系数(降低以避免数值溢出)
                    val factor = exp(-lambda * deltaSeconds)
                    return emotion * factor
                }

                /**
                 * 心情衰减函数 (修正版)
                 * 使用指数衰减模型，让 Mood 平滑回归到 Baseline
                 * 公式: V_new = V_base + (V_old - V_base) * e^(-λ * t)
                 */
                fun decayMood(
                    mood: PAD,
                    baseline: PAD,
                    deltaSeconds: Long
                ): PAD {
                    // P和A的衰减速率 (半衰期约 38.5小时)
                    val lambdaPA = 0.000005
                    // D的衰减速率 (半衰期约 19.2小时，维持原逻辑中 D 衰减更快的设定)
                    val lambdaD = 0.00001

                    // 计算衰减因子 (0.0 ~ 1.0)
                    // deltaSeconds 越大，factor 越接近 0，结果越接近 baseline
                    val factorPA = exp(-lambdaPA * deltaSeconds)
                    val factorD = exp(-lambdaD * deltaSeconds)

                    return PAD(
                        // P 和 A 向 baseline 回归
                        p = baseline.p + (mood.p - baseline.p) * factorPA,
                        a = baseline.a + (mood.a - baseline.a) * factorPA,

                        // D 也向 baseline.d 回归 (修复了之前无视 baseline.d 强制归零的bug)
                        d = baseline.d + (mood.d - baseline.d) * factorD
                    )
                }

                // 计算衰减后的情绪和心情
                val emotion = decayEmotion(currentEmotionEntity.emotion, seconds)
                val mood =
                    decayMood(currentEmotionEntity.mood, BotManage.getBot(currentBotId)!!.role.emoticon.pad, seconds)

                log.debug(
                    "群组 $groupId 情绪衰减: emotion P=${
                        String.format(
                            "%.2f",
                            emotion.p
                        )
                    }, A=${String.format("%.2f", emotion.a)}, D=${String.format("%.2f", emotion.d)}"
                )
                log.debug(
                    "群组 $groupId 心情衰减: mood P=${String.format("%.2f", mood.p)}, A=${
                        String.format(
                            "%.2f",
                            mood.a
                        )
                    }, D=${String.format("%.2f", mood.d)}"
                )

                // 更新情绪状态
                currentEmotionEntity.apply {
                    this.emotion = emotion
                    this.mood = mood
                }

                // 根据衰减后的情绪重新计算行为
                val behaviorProfile = BehaviorAnalysis(currentEmotionEntity).decideBehavior(
                    groupSize = groupSize,
                    adminPresent = adminPresent,
                    recentNegativeCount = 0,
                    currentStimulus = PAD.ZERO,
                    decay = Decay.LOW,
                    false
                )

                currentEmotionEntity.behavior = behaviorProfile
                currentEmotionEntity.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

                EventBus.postAsync(
                    EmotionChangeEvent(
                        currentBotId,
                        groupId,
                        mood
                    )
                )

                log.debug("群组 $groupId 情绪衰减完成")
            }
        }
    }

    /**
     * 情绪分析处理
     *
     * 读取群组的新消息,分析群聊氛围,更新机器人的情绪状态
     *
     * @param currentBotId 当前机器人ID
     * @param groupId 群组ID
     * @param groupSize 群组人数
     * @param adminPresent 是否有管理员在线
     */
    private suspend fun emotionAnalysis(
        currentBotId: String,
        groupId: String,
        groupSize: Int,
        adminPresent: Boolean
    ) {
        log.debug("开始分析群组情绪, groupId=$groupId")

        var historyEntities: List<HistoryEntity> = emptyList()
        var contextHistories: List<HistoryEntity> = emptyList()
        var currentEmotionEntity: EmotionEntity?

        // 1. 获取当前情绪状态和新消息
        withContext(Dispatchers.IO) {
            val emotionEntity = transaction {
                EmotionEntity.find {
                    EmotionTable.groupId eq groupId and
                            (EmotionTable.botMark eq currentBotId)
                }
                    .orderBy(EmotionTable.createdAt to SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
            }

            currentEmotionEntity = emotionEntity

            val lastProcessedId = emotionEntity?.historyMessageProcessed ?: 0
            log.debug("群组 $groupId 上次处理到 historyId=$lastProcessedId")

            // 获取上下文消息(之前的100条,用于提供上下文)
            if (lastProcessedId > 0) {
                contextHistories = transaction {
                    HistoryEntity.find {
                        (HistoryTable.botMark eq currentBotId) and
                                (HistoryTable.groupId eq groupId) and
                                (HistoryTable.id less lastProcessedId + 1)
                    }
                        .orderBy(HistoryTable.createdAt to SortOrder.DESC)
                        .limit(100)
                        .toList()
                        .reversed()  // 反转为正序
                }
                log.debug("群组 $groupId 获取到 ${contextHistories.size} 条上下文消息")
            }

            // 获取新消息(最多200条)
            historyEntities = transaction {
                HistoryEntity.find {
                    HistoryTable.botMark eq currentBotId and
                            (HistoryTable.groupId eq groupId) and
                            (HistoryTable.id greater lastProcessedId)
                }
                    .orderBy(HistoryTable.createdAt to SortOrder.ASC)
                    .limit(200)
                    .toList()
            }
        }

        // 2. 检查消息数量是否足够
        if (historyEntities.isEmpty() || historyEntities.size <= 10) {
            log.debug("群组 $groupId 新消息数量不足(${historyEntities.size}条), 跳过分析")
            return
        }

        log.debug("群组 $groupId 获取到 ${historyEntities.size} 条新消息, ${contextHistories.size} 条上下文消息, 开始情感分析")

        // 3. 合并上下文和新消息,转换为 GMessage 格式
        val allHistories = contextHistories + historyEntities
        val gMessages = allHistories.map {
            GMessage(
                serial = it.id.value,
                userId = it.userId,
                role = if (it.userId == currentBotId) GMessage.Role.SELF else GMessage.Role.OTHER,
                time = it.createdAt,
                content = it.content ?: ""
            )
        }.toList()

        log.debug("群组 $groupId 总共分析 ${gMessages.size} 条消息(上下文${contextHistories.size} + 新消息${historyEntities.size})")

        // 4. 调用 LLM 分析情感刺激值
        val stimulus = analyzeStimulus(gMessages)
        log.debug(
            "群组 $groupId 情感刺激值: P=${String.format("%.2f", stimulus.p)}, A=${
                String.format(
                    "%.2f",
                    stimulus.a
                )
            }, D=${String.format("%.2f", stimulus.d)}"
        )

        // 5. 创建行为分析器
        val behaviorAnalysis = BehaviorAnalysis(
            currentEmotionEntity,
            BotManage.getBot(currentBotId)!!.role.emoticon
        )

        // 6. 确定衰减等级(根据新消息数量)
        val decay = when {
            historyEntities.size > 150 -> Decay.HIGH
            historyEntities.size > 80 -> Decay.MEDIUM
            else -> Decay.LOW
        }
        log.debug("群组 {} 衰减等级: {} (新消息数={})", groupId, decay, historyEntities.size)

        // 7. 计算新的情绪和心情
        val emotion = behaviorAnalysis.decideEmotion(stimulus, decay)
        val mood = behaviorAnalysis.decideMood(stimulus, decay)

        log.debug(
            "群组 $groupId 新情绪状态: emotion P=${String.format("%.2f", emotion.p)}, A=${
                String.format(
                    "%.2f",
                    emotion.a
                )
            }, D=${String.format("%.2f", emotion.d)}"
        )
        log.debug(
            "群组 $groupId 新心情状态: mood P=${String.format("%.2f", mood.p)}, A=${
                String.format(
                    "%.2f",
                    mood.a
                )
            }, D=${String.format("%.2f", mood.d)}"
        )

        // 8. 计算行为表现
        val behaviorProfile = behaviorAnalysis.decideBehavior(
            groupSize = groupSize,
            adminPresent = adminPresent,
            recentNegativeCount = 0,
            currentStimulus = stimulus,
            decay = decay
        )

        log.debug(
            "群组 {} 行为表现: emotion={}, tone={}, aggressiveness={}, emojiLevel={}",
            groupId,
            behaviorProfile.emotion,
            behaviorProfile.tone,
            behaviorProfile.aggressiveness,
            behaviorProfile.emojiLevel
        )

        // 9. 保存新的情绪状态
        withContext(Dispatchers.IO) {
            transaction {
                EmotionEntity.new {
                    botMark = currentBotId
                    this.groupId = groupId
                    emotionalTendency = behaviorProfile.emotion
                    this.stimulus = stimulus
                    this.emotion = emotion
                    this.mood = mood
                    behavior = behaviorProfile
                    historyMessageProcessed = historyEntities.maxOf { it.id.value }
                }
                log.debug("群组 $groupId 情绪状态已保存, 处理到 historyId=${historyEntities.maxOf { it.id.value }}")
                EventBus.postAsync(
                    EmotionChangeEvent(
                        currentBotId,
                        groupId,
                        emotion
                    )
                )
            }
        }
    }

}