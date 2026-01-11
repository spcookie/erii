package uesugi.core.emotion

import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

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
        private const val y = 0.05
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

class EmotionService {
    fun getCurrentBehaviorProfile(botMark: String, groupId: String): BehaviorProfile? {
        return transaction {
            EmotionEntity.find {
                EmotionTable.botMark eq botMark and
                        (EmotionTable.groupId eq groupId)
            }.firstOrNull()?.behavior
        }
    }
}