package uesugi.core.state.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.toolkit.ConfigHolder
import uesugi.common.toolkit.logger
import uesugi.core.state.summary.SummaryEntity
import uesugi.core.state.summary.SummaryTable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

/**
 * 记忆服务 - 负责记忆处理的业务逻辑
 */
class MemoryService(
    private val memoryAgent: MemoryAgent,
    private val memoryRepository: MemoryRepository,
    private val factVectorStore: FactVectorStore
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
            val tuning = ConfigHolder.getStateTuning().memory
            val memoryState = memoryRepository.getMemoryState(botMark, groupId)
            val lastId = memoryState?.lastProcessedHistoryId ?: 0

            val histories = withContext(Dispatchers.IO) {
                memoryRepository.getHistoriesToProcess(botMark, groupId, lastId, tuning.batchLimit)
            }

            if (histories.isEmpty()) {
                log.debug("群组 $groupId 没有新消息需要处理")
                return
            }

            if (histories.size < tuning.minMessages) {
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
            val processedSuccessfully = coroutineScope {
                // 4.1 用户画像和偏好 (按用户)
                val profileJob = async {
                    messagesByUser
                        .filterKeys { it != botMark }
                        .all { (userId, userMessages) ->
                            processUserProfile(botMark, groupId, userId, userMessages)
                        }
                }

                // 4.2 事实记忆提取
                val factsJob = async {
                    organizeFacts(botMark, groupId, messages)
                }

                profileJob.await() && factsJob.await()
            }

            if (!processedSuccessfully) {
                log.warn("Memory processing partially failed, keeping cursor for retry, groupId=$groupId")
                return
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
     * 整理事实记忆
     *
     * 流程：
     * 1. LLM 提取事实（extractFacts）
     * 2. LLM 冲突解决（resolveConflicts）→ ADD / UPDATE / DELETE / NONE
     * 3. 批量执行决策
     * 4. 统一向量同步
     */
    private suspend fun organizeFacts(
        botMark: String,
        groupId: String,
        messages: List<MemoryAgent.MemoryMessage>
    ): Boolean {
        try {
            log.debug("开始整理事实记忆, groupId=$groupId, message count=${messages.size}")
            memoryAgent.organize(botMark, groupId, messages)

            log.info("Fact memory sorting completed, botId=$botMark, groupId=$groupId")
            return true
        } catch (e: Exception) {
            log.error("Failed to organize fact memory, groupId=$groupId", e)
            return false
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
    ): Boolean {
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
            return true

        } catch (e: Exception) {
            log.error("Failed to process user portrait, groupId=$groupId, userId=$userId", e)
            return false
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
                        .map { it.trim() }
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

    suspend fun getFactsWithVector(
        botMark: String,
        groupId: String,
        subjects: List<String>,
        query: String,
        limit: Int = 15
    ): List<FactsEntity> {
        val dbFacts = getFacts(botMark, groupId, subjects, limit)

        if (query.isBlank()) {
            markFactsRecalled(dbFacts)
            return dbFacts
        }

        val vectorResults = try {
            factVectorStore.search(query, groupId, botMark, limit)
        } catch (e: Exception) {
            log.warn("Vector search failed for facts, falling back to DB only", e)
            emptyList()
        }

        val dbFactIds = dbFacts.map { it.id.value }.toSet()

        val newVectorFacts = withContext(Dispatchers.IO) {
            transaction {
                val factIds = vectorResults
                    .mapNotNull { it.factId }
                    .filter { it !in dbFactIds }

                if (factIds.isEmpty()) {
                    emptyList()
                } else {
                    FactsEntity.find {
                        (FactsTable.id inList factIds) and
                                (FactsTable.validFrom lessEq CurrentDateTime) and
                                (FactsTable.validTo.isNull() or (FactsTable.validTo greater CurrentDateTime))
                    }.toList()
                }
            }
        }

        val recalledFacts = dbFacts + newVectorFacts
        markFactsRecalled(recalledFacts)
        return recalledFacts
    }

    private suspend fun markFactsRecalled(facts: List<FactsEntity>) {
        val ids = facts.map { it.id.value }.distinct()
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            memoryRepository.markFactsRecalled(ids)
        }
    }

    fun deleteExpiredFacts(): Int {
        val deletedFacts = memoryRepository.deleteExpiredFacts()
        deleteVectors(deletedFacts)
        log.info("Expired fact memory cleanup completed, deleted=${deletedFacts.size}")
        return deletedFacts.size
    }

    @OptIn(ExperimentalTime::class)
    fun deleteStaleUnrecalledFacts(staleRecallDays: Long): Int {
        val cutoff = Clock.System.now()
            .minus(staleRecallDays.days)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val deletedFacts = memoryRepository.deleteStaleUnrecalledFacts(cutoff)
        deleteVectors(deletedFacts)
        log.info("Stale unrecalled fact memory cleanup completed, days=$staleRecallDays, deleted=${deletedFacts.size}")
        return deletedFacts.size
    }

    private fun deleteVectors(facts: List<FactsRecord>) {
        facts.forEach { fact ->
            fact.vectorId?.let { vectorId ->
                factVectorStore.deleteVector(vectorId, fact.botMark, fact.groupId)
            }
        }
    }

    fun getAllFactsByGroup(
        botMark: String,
        groupId: String,
        offset: Int = 0,
        limit: Int = 0
    ): Pair<List<FactsEntity>, Int> {
        return transaction {
            val baseQuery = FactsEntity.find {
                (FactsTable.botMark eq botMark) and
                        (FactsTable.groupId eq groupId) and
                        (FactsTable.validFrom lessEq CurrentDateTime) and
                        (FactsTable.validTo.isNull() or (FactsTable.validTo greater CurrentDateTime))
            }
            val total = baseQuery.count().toInt()
            val items = if (limit > 0) {
                baseQuery.orderBy(FactsTable.createdAt to SortOrder.DESC)
                    .limit(limit, offset.toLong())
                    .reversed()
                    .toList()
            } else {
                baseQuery.orderBy(FactsTable.createdAt to SortOrder.DESC)
                    .drop(offset)
                    .reversed()
                    .toList()
            }
            items to total
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
        groupId: String,
        offset: Int = 0,
        limit: Int = 0
    ): Pair<List<UserProfileEntity>, Int> {
        return transaction {
            val baseQuery = UserProfileEntity.find {
                (UserProfileTable.botMark eq botMark) and
                        (UserProfileTable.groupId eq groupId)
            }
            val total = baseQuery.count().toInt()
            val items = if (limit > 0) {
                baseQuery.orderBy(UserProfileTable.createdAt to SortOrder.DESC)
                    .limit(limit, offset.toLong())
                    .reversed()
                    .toList()
            } else {
                baseQuery.orderBy(UserProfileTable.createdAt to SortOrder.DESC)
                    .drop(offset)
                    .reversed()
                    .toList()
            }
            items to total
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

    fun deleteFact(botId: String, groupId: String, id: Int): Boolean {
        val fact = memoryRepository.getFactById(id) ?: return false
        if (fact.botMark != botId || fact.groupId != groupId) return false
        fact.vectorId?.let { vectorId ->
            factVectorStore.deleteVector(vectorId, botId, groupId)
        }
        return memoryRepository.deleteFact(id)
    }
}
