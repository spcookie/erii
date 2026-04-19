package uesugi.core.state.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.toolkit.logger
import uesugi.core.state.summary.SummaryEntity
import uesugi.core.state.summary.SummaryTable

/**
 * 记忆服务 - 负责记忆处理的业务逻辑
 */
class MemoryService(
    private val memoryAgent: MemoryAgent,
    private val memoryRepository: MemoryRepository
) {

    companion object {
        private val log = logger()
    }

    /**
     * 处理单个群组的记忆
     */
    suspend fun processGroupMemory(botMark: String, groupId: String) {
        log.debug("开始处理群组记忆, groupId=$groupId")

        try {
            // 1. 获取需要处理的历史消息
            val memoryState = memoryRepository.getMemoryState(botMark, groupId)
            val lastId = memoryState?.lastProcessedHistoryId ?: 0

            val histories = withContext(Dispatchers.IO) {
                memoryRepository.getHistoriesToProcess(botMark, groupId, lastId, 400)
            }

            if (histories.isEmpty()) {
                log.debug("群组 $groupId 没有新消息需要处理")
                return
            }

            if (histories.size < 30) {
                log.debug("群组 $groupId 消息数量不足 30 条，跳过记忆处理")
                return
            }

            log.debug("群组 $groupId 获取到 ${histories.size} 条新消息")

            // 2. 转换为记忆消息
            val messages = memoryAgent.convertToMemoryMessages(histories)

            if (messages.isEmpty()) {
                log.debug("群组 $groupId 消息转换后为空,跳过处理")
                val maxHistoryId = histories.maxOf { it.id!! }
                memoryRepository.updateMemoryState(botMark, groupId, maxHistoryId)
                return
            }

            // 3. 按用户分组
            val messagesByUser = messages.groupBy { it.userId }

            // 4. 并发处理
            coroutineScope {
                // 4.1 用户画像和偏好 (按用户)
                launch {
                    for ((userId, userMessages) in messagesByUser) {
                        if (userId != botMark) {
                            processUserProfile(botMark, groupId, userId, userMessages)
                        }
                    }
                }

                // 4.2 事实记忆提取
                launch {
                    organizeFacts(botMark, groupId, messages)
                }
            }

            // 5. 更新记忆处理状态
            val maxHistoryId = histories.maxOf { it.id!! }
            memoryRepository.updateMemoryState(botMark, groupId, maxHistoryId)

            log.debug("群组 $groupId 记忆处理完成, 最大 historyId=$maxHistoryId")

        } catch (e: Exception) {
            log.error("处理群组 $groupId 记忆失败", e)
        }
    }

    /**
     * 使用 Agent + Tool 方式整理事实记忆
     * Agent 自主分析消息和现有事实，调用工具执行操作（向量同步已内联）
     */
    private suspend fun organizeFacts(
        botMark: String,
        groupId: String,
        messages: List<MemoryAgent.MemoryMessage>
    ) {
        try {
            log.debug("开始使用 Agent 整理事实记忆, groupId=$groupId, 消息数=${messages.size}")
            // 调用 organize 方法（内部使用 Agent + Tool 方式，向量同步已内联）
            memoryAgent.organize(botMark, groupId, messages)

            log.info("Fact memory sorting completed, botId=$botMark, groupId=$groupId")
        } catch (e: Exception) {
            log.error("Failed to organize fact memory, groupId=$groupId", e)
        }
    }

    /**
     * 处理用户画像
     */
    private suspend fun processUserProfile(
        botMark: String,
        groupId: String,
        userId: String,
        messages: List<MemoryAgent.MemoryMessage>
    ) {
        try {
            log.debug("开始处理用户画像, groupId=$groupId, userId=$userId")

            val existing = withContext(Dispatchers.IO) {
                memoryRepository.findOrCreateUserProfile(botMark, groupId, userId)
            }

            val analysis = memoryAgent.analyzeUserProfile(messages, existing)

            // 保存到数据库
            withContext(Dispatchers.IO) {
                memoryRepository.updateUserProfile(botMark, groupId, userId, analysis.profile, analysis.preferences)
                log.info("User portrait has been updated, botId=$botMark, groupId=$groupId, userId=$userId")
            }

        } catch (e: Exception) {
            log.error("Failed to process user portrait, groupId=$groupId, userId=$userId", e)
        }
    }

    fun getFacts(
        botMark: String,
        groupId: String,
        subjects: List<String>,
        limit: Int = 25
    ): List<FactsEntity> {
        return transaction {
            val userFacts = FactsEntity.find {
                (FactsTable.botMark eq botMark) and
                        (FactsTable.groupId eq groupId) and
                        (FactsTable.scopeType eq Scopes.USER) and
                        (FactsTable.validFrom lessEq CurrentDateTime) and
                        (FactsTable.validTo.isNull() or (FactsTable.validTo greater CurrentDateTime))
            }.orderBy(FactsTable.createdAt to SortOrder.DESC)
                .limit(limit)
                .reversed()
                .filter { fact ->
                    fact.subjects.split(",")
                        .any { subjects.contains(it) }
                }
            val groupFacts = FactsEntity.find {
                (FactsTable.botMark eq botMark) and
                        (FactsTable.groupId eq groupId) and
                        (FactsTable.scopeType eq Scopes.GROUP) and
                        (FactsTable.validFrom lessEq CurrentDateTime) and
                        (FactsTable.validTo.isNull() or (FactsTable.validTo greater CurrentDateTime))
            }.orderBy(FactsTable.createdAt to SortOrder.DESC)
                .limit(limit)
                .reversed()
            userFacts + groupFacts
        }
    }

    fun getAllFactsByGroup(
        botMark: String,
        groupId: String
    ): List<FactsEntity> {
        return transaction {
            FactsEntity.find {
                (FactsTable.botMark eq botMark) and
                        (FactsTable.groupId eq groupId) and
                        (FactsTable.validFrom lessEq CurrentDateTime) and
                        (FactsTable.validTo.isNull() or (FactsTable.validTo greater CurrentDateTime))
            }.orderBy(FactsTable.createdAt to SortOrder.DESC)
                .reversed()
                .toList()
        }
    }

    fun getFactSize(
        botMark: String,
        groupId: String
    ): Long {
        return transaction {
            FactsEntity.find {
                (FactsTable.botMark eq botMark) and
                        (FactsTable.groupId eq groupId)
            }.count()
        }
    }

    fun getUserProfiles(
        botMark: String,
        groupId: String,
        userId: List<String>
    ): List<UserProfileEntity> {
        return transaction {
            UserProfileEntity.find {
                (UserProfileTable.botMark eq botMark) and
                        (UserProfileTable.groupId eq groupId) and
                        (UserProfileTable.userId inList userId)
            }.toList()
        }
    }

    fun getAllUserProfilesByGroup(
        botMark: String,
        groupId: String
    ): List<UserProfileEntity> {
        return transaction {
            UserProfileEntity.find {
                (UserProfileTable.botMark eq botMark) and
                        (UserProfileTable.groupId eq groupId)
            }.orderBy(UserProfileTable.createdAt to SortOrder.DESC)
                .reversed()
                .toList()
        }
    }

    fun getUserProfileSize(
        botMark: String,
        groupId: String
    ): Long {
        return transaction {
            UserProfileEntity.find {
                (UserProfileTable.botMark eq botMark) and
                        (UserProfileTable.groupId eq groupId)
            }.count()
        }
    }

    fun getSummary(
        botMark: String,
        groupId: String
    ): SummaryEntity? {
        return transaction {
            SummaryEntity.find {
                (SummaryTable.botMark eq botMark) and
                        (SummaryTable.groupId eq groupId)
            }.orderBy(SummaryTable.createdAt to SortOrder.DESC)
                .firstOrNull()
        }
    }

}