package uesugi.core.state.emotion

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.BotManage
import uesugi.common.EmotionalTendencies
import uesugi.common.EventBus
import uesugi.common.PAD
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

object BehaviorMapper {

    fun fromEmotion(emotion: EmotionalTendencies): BehaviorProfile =
        when (emotion) {

            EmotionalTendencies.JOY,
            EmotionalTendencies.OPTIMISM ->
                BehaviorProfile(
                    emotion = emotion,
                    tone = Tone.FRIENDLY,
                    aggressiveness = Aggressiveness.NONE,
                    emojiLevel = EmojiLevel.HIGH,
                )

            EmotionalTendencies.RELAXATION,
            EmotionalTendencies.MILDNESS ->
                BehaviorProfile(
                    emotion = emotion,
                    tone = Tone.GENTLE,
                    aggressiveness = Aggressiveness.NONE,
                    emojiLevel = EmojiLevel.LOW,
                )

            EmotionalTendencies.BOREDOM ->
                BehaviorProfile(
                    emotion = emotion,
                    tone = Tone.NEUTRAL,
                    aggressiveness = Aggressiveness.TEASING,
                    emojiLevel = EmojiLevel.NONE,
                )

            EmotionalTendencies.SADNESS,
            EmotionalTendencies.ANXIETY ->
                BehaviorProfile(
                    emotion = emotion,
                    tone = Tone.LOW_ENERGY,
                    aggressiveness = Aggressiveness.NONE,
                    emojiLevel = EmojiLevel.LOW,
                )

            EmotionalTendencies.FEAR ->
                BehaviorProfile(
                    emotion = emotion,
                    tone = Tone.NEUTRAL,
                    aggressiveness = Aggressiveness.NONE,
                    emojiLevel = EmojiLevel.NONE,
                )

            EmotionalTendencies.CONTEMPT,
            EmotionalTendencies.DISGUST,
            EmotionalTendencies.RESENTMENT,
            EmotionalTendencies.HOSTILITY ->
                BehaviorProfile(
                    emotion = emotion,
                    tone = Tone.IRONIC,
                    aggressiveness = Aggressiveness.ABSTRACT_SARCASM,
                    emojiLevel = EmojiLevel.LOW,
                )

            EmotionalTendencies.SURPRISE ->
                BehaviorProfile(
                    emotion = emotion,
                    tone = Tone.FRIENDLY,
                    aggressiveness = Aggressiveness.NONE,
                    emojiLevel = EmojiLevel.MEDIUM,
                )

            EmotionalTendencies.DEPENDENCE ->
                BehaviorProfile(
                    emotion = emotion,
                    tone = Tone.GENTLE,
                    aggressiveness = Aggressiveness.NONE,
                    emojiLevel = EmojiLevel.LOW,
                )

        }
}

object SafetyGate {

    fun apply(
        profile: BehaviorProfile,
        groupSize: Int,
        adminPresent: Boolean,
        recentNegativeCount: Int
    ): BehaviorProfile {

        var result = profile

        // 群大 or 管理员在场 → 禁止任何攻击倾向
        if (groupSize > 20 || adminPresent) {
            result = result.copy(
                aggressiveness = Aggressiveness.NONE
            )
        }

        // 连续负面 → 强制冷却
        if (recentNegativeCount >= 2) {
            result = result.copy(
                emojiLevel = EmojiLevel.NONE,
            )
        }

        return result
    }
}

class BehaviorAnalysis(
    private val currentEmotionEntity: EmotionEntity?,
    private val baseLine: EmotionalTendencies? = null
) {

    companion object {
        private const val y = 0.1
    }

    fun decideEmotion(
        currentStimulus: Stimulus,
        decay: Decay,
    ): Emotion {
        return if (currentEmotionEntity == null) {
            if (baseLine != null) {
                baseLine.pad * decay.decay + currentStimulus
            } else {
                currentStimulus
            }
        } else {
            currentEmotionEntity.emotion * decay.decay + currentStimulus
        }
    }

    fun decideMood(
        currentStimulus: Stimulus,
        decay: Decay,
    ): Emotion {
        return if (currentEmotionEntity == null) {
            if (baseLine != null) {
                baseLine.pad + currentStimulus * y
            } else {
                currentStimulus * y
            }
        } else {
            val emotion = currentEmotionEntity.emotion * decay.decay + currentStimulus
            currentEmotionEntity.mood + emotion * y
        }
    }

    fun decideBehavior(
        groupSize: Int,
        adminPresent: Boolean,
        recentNegativeCount: Int,
        currentStimulus: Stimulus,
        decay: Decay,
        applyEmotion: Boolean = true
    ): BehaviorProfile {
        val emotion = if (currentEmotionEntity == null) {
            currentStimulus
        } else {
            currentEmotionEntity.emotion * decay.decay + currentStimulus
        }
        val mood = if (currentEmotionEntity == null) {
            emotion * y
        } else {
            currentEmotionEntity.mood + emotion * y
        }

        val emotional = EmotionalTendencies.findClosest(if (applyEmotion) emotion else mood)

        val baseProfile = BehaviorMapper.fromEmotion(emotional)

        return SafetyGate.apply(
            profile = baseProfile,
            groupSize = groupSize,
            adminPresent = adminPresent,
            recentNegativeCount = recentNegativeCount
        )
    }
}

class EmotionService(
    private val emotionRepository: EmotionRepository
) {

    /**
     * 处理单个群组的情绪分析
     */
    suspend fun analyzeGroupEmotion(
        currentBotId: String,
        groupId: String,
        groupSize: Int,
        adminPresent: Boolean
    ) {
        // 获取当前情绪状态和新消息
        val emotionEntity = emotionRepository.getLatestEmotion(currentBotId, groupId)

        val lastProcessedId = emotionEntity?.historyMessageProcessed ?: 0

        // 获取上下文消息
        val contextHistories = if (lastProcessedId > 0) {
            emotionRepository.getContextMessages(currentBotId, groupId, lastProcessedId, 100)
        } else {
            emptyList()
        }

        // 获取新消息
        val historyEntities = emotionRepository.getNewMessages(currentBotId, groupId, lastProcessedId, 200)

        // 检查消息数量
        if (historyEntities.isEmpty() || historyEntities.size <= 10) {
            return
        }

        // 转换为 GMessage
        val allHistories = contextHistories + historyEntities
        val gMessages = allHistories.map {
            GMessage(
                serial = it.id.value,
                userId = it.userId,
                role = if (it.userId == currentBotId) GMessage.Role.SELF else GMessage.Role.OTHER,
                time = it.createdAt,
                content = it.content ?: ""
            )
        }

        // 调用 LLM 分析情感刺激值
        val stimulus = analyzeStimulus(gMessages)

        // 创建行为分析器
        val behaviorAnalysis = BehaviorAnalysis(
            emotionEntity,
            BotManage.getBot(currentBotId).role.emoticon
        )

        // 确定衰减等级
        val decay = when {
            historyEntities.size > 150 -> Decay.HIGH
            historyEntities.size > 80 -> Decay.MEDIUM
            else -> Decay.LOW
        }

        // 计算新的情绪和心情
        val emotion = behaviorAnalysis.decideEmotion(stimulus, decay)
        val mood = behaviorAnalysis.decideMood(stimulus, decay)

        // 计算行为表现
        val behaviorProfile = behaviorAnalysis.decideBehavior(
            groupSize = groupSize,
            adminPresent = adminPresent,
            recentNegativeCount = 0,
            currentStimulus = stimulus,
            decay = decay
        )

        // 保存情绪状态
        val maxHistoryId = historyEntities.maxOf { it.id.value }
        emotionRepository.saveEmotion(
            botMark = currentBotId,
            groupId = groupId,
            emotionalTendency = behaviorProfile.emotion,
            stimulus = stimulus,
            emotion = emotion,
            mood = mood,
            behavior = behaviorProfile,
            historyMessageProcessed = maxHistoryId
        )

        // 发送事件
        EventBus.postAsync(EmotionChangeEvent(currentBotId, groupId, emotion))
    }

    /**
     * 处理单个群组的情绪衰减
     */
    @OptIn(ExperimentalTime::class)
    fun decayGroupEmotion(
        currentBotId: String,
        groupId: String,
        groupSize: Int,
        adminPresent: Boolean
    ) {
        val currentEmotionEntity = emotionRepository.getLatestEmotion(currentBotId, groupId) ?: return

        // 计算时间差
        val tz = TimeZone.currentSystemDefault()
        val now = Clock.System.now()
        val instant = currentEmotionEntity.updatedAt.toInstant(tz)
        val seconds = (now - instant).inWholeSeconds.coerceAtLeast(0)

        // 计算衰减
        val emotion = emotionRepository.calculateDecayEmotion(currentEmotionEntity.emotion, seconds)
        val mood = emotionRepository.calculateDecayMood(
            currentEmotionEntity.mood,
            BotManage.getBot(currentBotId).role.emoticon.pad,
            seconds
        )

        // 计算行为
        val hours = (now - currentEmotionEntity.createdAt.toInstant(tz)).inWholeHours.coerceAtLeast(0)
        val applyEmotion = hours < 1
        val behaviorProfile = BehaviorAnalysis(currentEmotionEntity).decideBehavior(
            groupSize = groupSize,
            adminPresent = adminPresent,
            recentNegativeCount = 0,
            currentStimulus = PAD.ZERO,
            decay = Decay.LOW,
            applyEmotion = applyEmotion
        )

        // 保存衰减后的情绪
        emotionRepository.saveEmotion(
            botMark = currentBotId,
            groupId = groupId,
            emotionalTendency = behaviorProfile.emotion,
            stimulus = currentEmotionEntity.stimulus,
            emotion = emotion,
            mood = mood,
            behavior = behaviorProfile,
            historyMessageProcessed = currentEmotionEntity.historyMessageProcessed
        )

        // 发送事件
        EventBus.postAsync(EmotionChangeEvent(currentBotId, groupId, mood))
    }

    // ====== 原有查询方法 ======

    fun getCurrentBehaviorProfile(botMark: String, groupId: String): BehaviorProfile? {
        return transaction {
            EmotionEntity.find {
                EmotionTable.botMark eq botMark and
                        (EmotionTable.groupId eq groupId)
            }.firstOrNull()?.behavior
        }
    }

    @OptIn(ExperimentalTime::class)
    fun getCurrentMood(botMark: String, groupId: String): PAD? {
        return transaction {
            EmotionEntity.find {
                EmotionTable.botMark eq botMark and
                        (EmotionTable.groupId eq groupId)
            }.firstOrNull()?.let {
                val tz = TimeZone.currentSystemDefault()
                val now = Clock.System.now()
                val instant = it.updatedAt.toInstant(tz)
                val hours = (now - instant).inWholeHours.coerceAtLeast(0)
                if (hours < 1) {
                    it.emotion
                } else {
                    it.mood
                }
            }
        }
    }
}