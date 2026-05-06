package uesugi.core.state.summary

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uesugi.common.toolkit.logger
import uesugi.core.state.memory.MemoryAgent
import uesugi.core.state.memory.MemoryRepository

/**
 * 摘要服务 - 负责对话摘要生成的业务逻辑
 */
class SummaryService(
    private val memoryRepository: MemoryRepository,
    private val summaryRepository: SummaryRepository,
    private val memoryAgent: MemoryAgent
) {

    companion object {
        private val log = logger()
    }

    /**
     * 处理群组对话摘要
     */
    suspend fun processSummaryForGroup(botMark: String, groupId: String) {
        try {
            log.debug("开始处理群组对话摘要, groupId=$groupId")

            // 1. 获取需要处理的历史消息
            val memoryState = memoryRepository.getMemoryState(botMark, groupId)
            val lastId = memoryState?.lastProcessedHistoryId ?: 0

            val histories = withContext(Dispatchers.IO) {
                memoryRepository.getHistoriesToProcess(botMark, groupId, lastId, 200)
            }

            if (histories.isEmpty()) {
                log.debug("群组 $groupId 没有新消息需要处理摘要")
                return
            }

            if (histories.size < 30) {
                log.debug("群组 $groupId 消息数量不足 30 条，跳过摘要生成")
                return
            }

            // 2. 转换为记忆消息
            val messages = memoryAgent.convertToMemoryMessages(histories)

            if (messages.isEmpty()) {
                log.debug("群组 $groupId 消息转换后为空,跳过摘要生成")
                return
            }

            // 3. 生成摘要
            generateSummary(botMark, groupId, messages)

            log.debug("群组 $groupId 对话摘要生成完成")

        } catch (e: Exception) {
            log.error("处理群组 $groupId 对话摘要失败", e)
        }
    }

    /**
     * 生成对话摘要
     */
    private suspend fun generateSummary(
        botMark: String,
        groupId: String,
        messages: List<MemoryAgent.MemoryMessage>
    ) {
        try {
            log.debug("开始生成对话摘要, scopeId=$groupId")

            val summary = memoryAgent.generateSummary(messages, groupId)

            // 保存到数据库
            summaryRepository.saveSummary(
                botMark = botMark,
                groupId = groupId,
                timeRange = summary.timeRange,
                content = summary.content,
                keyPoints = summary.keyPoints.joinToString("\n"),
                emotionalTone = summary.emotionalTone,
                participantCount = summary.participantIds.distinct().size,
                messageCount = summary.messageCount
            )
            log.info("Conversation summary analysis completed, botId=$botMark, groupId=$groupId")

        } catch (e: Exception) {
            log.error("Failed to generate conversation summary, groupId=$groupId", e)
        }
    }

    fun getAllSummariesByGroup(botMark: String, groupId: String): List<SummaryRecord> {
        return summaryRepository.getSummariesByGroup(botMark, groupId)
    }

    fun getSummaryById(id: Int): SummaryRecord? {
        return summaryRepository.getSummaryById(id)
    }

    fun updateSummary(
        id: Int,
        timeRange: String,
        content: String,
        keyPoints: String,
        emotionalTone: String?
    ): SummaryRecord? {
        return summaryRepository.updateSummary(
            id, timeRange, content, keyPoints, emotionalTone
        )
    }

    fun deleteSummary(id: Int): Boolean {
        return summaryRepository.deleteSummary(id)
    }
}
