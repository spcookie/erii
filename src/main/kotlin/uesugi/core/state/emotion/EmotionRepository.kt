package uesugi.core.state.emotion

import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.core.message.history.HistoryEntity
import uesugi.core.message.history.HistoryTable
import uesugi.toolkit.logger
import kotlin.math.exp

/**
 * 情绪仓库 - 负责数据库操作
 */
class EmotionRepository {

    companion object {
        private val log = logger()
    }

    /**
     * 查找需要分析的群组（新消息的群组）
     */
    fun findGroupsNeedAnalysis(botMark: String): List<String> {
        return transaction {
            EmotionEntity.findRequiredAnalysisHistoryGroupIds(botMark)
        }
    }

    /**
     * 查找不需要分析的群组（无新消息的群组）
     */
    fun findGroupsNotNeedAnalysis(botMark: String, excludeGroups: List<String>): List<String> {
        return transaction {
            EmotionEntity.findNotAnalysisHistoryGroupIds(botMark, excludeGroups)
        }
    }

    /**
     * 获取群组的最新情绪记录
     */
    fun getLatestEmotion(botMark: String, groupId: String): EmotionEntity? {
        return transaction {
            EmotionEntity.find(
                (EmotionTable.groupId eq groupId) and (EmotionTable.botMark eq botMark)
            )
                .orderBy(EmotionTable.createdAt to SortOrder.DESC)
                .limit(1)
                .firstOrNull()
        }
    }

    /**
     * 获取新消息（用于情绪分析）
     */
    fun getNewMessages(botMark: String, groupId: String, lastProcessedId: Int, limit: Int = 200): List<HistoryEntity> {
        return transaction {
            HistoryEntity.find(
                (HistoryTable.botMark eq botMark) and
                        (HistoryTable.groupId eq groupId) and
                        (HistoryTable.id greater lastProcessedId)
            )
                .orderBy(HistoryTable.createdAt to SortOrder.ASC)
                .limit(limit)
                .toList()
        }
    }

    /**
     * 获取上下文消息（用于情绪分析）
     */
    fun getContextMessages(botMark: String, groupId: String, beforeId: Int, limit: Int = 100): List<HistoryEntity> {
        return transaction {
            HistoryEntity.find(
                (HistoryTable.botMark eq botMark) and
                        (HistoryTable.groupId eq groupId) and
                        (HistoryTable.id less beforeId + 1)
            )
                .orderBy(HistoryTable.createdAt to SortOrder.DESC)
                .limit(limit)
                .toList()
                .reversed()
        }
    }

    /**
     * 保存情绪记录
     */
    fun saveEmotion(
        botMark: String,
        groupId: String,
        emotionalTendency: EmotionalTendencies,
        stimulus: PAD,
        emotion: PAD,
        mood: PAD,
        behavior: BehaviorProfile,
        historyMessageProcessed: Int
    ) {
        transaction {
            EmotionEntity.new {
                this.botMark = botMark
                this.groupId = groupId
                this.emotionalTendency = emotionalTendency
                this.stimulus = stimulus
                this.emotion = emotion
                this.mood = mood
                this.behavior = behavior
                this.historyMessageProcessed = historyMessageProcessed
            }
            log.debug("群组 $groupId 情绪状态已保存, 处理到 historyId=$historyMessageProcessed")
        }
    }

    /**
     * 情绪衰减计算
     */
    fun calculateDecayEmotion(emotion: PAD, deltaSeconds: Long): PAD {
        val lambda = 0.0001
        val factor = exp(-lambda * deltaSeconds)
        return emotion * factor
    }

    /**
     * 心情衰减计算
     */
    fun calculateDecayMood(mood: PAD, baseline: PAD, deltaSeconds: Long): PAD {
        val lambdaPA = 0.000005
        val lambdaD = 0.00001
        val factorPA = exp(-lambdaPA * deltaSeconds)
        val factorD = exp(-lambdaD * deltaSeconds)

        return PAD(
            p = baseline.p + (mood.p - baseline.p) * factorPA,
            a = baseline.a + (mood.a - baseline.a) * factorPA,
            d = baseline.d + (mood.d - baseline.d) * factorD
        )
    }
}
