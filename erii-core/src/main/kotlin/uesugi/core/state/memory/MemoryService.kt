package uesugi.core.state.memory

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import uesugi.common.logger
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
            val memoryState = memoryRepository.getMemoryState(botMark, groupId)
            val lastId = memoryState?.lastProcessedHistoryId ?: 0

            val histories = withContext(Dispatchers.IO) {
                memoryRepository.getHistoriesToProcess(botMark, groupId, lastId, 200)
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

            // 4. 并发处理各类记忆生成
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

                // 4.3 对话摘要生成
                launch {
                    processSummary(botMark, groupId, messages)
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

            log.info("事实记忆整理完成, botId=$botMark, groupId=$groupId")
        } catch (e: Exception) {
            log.error("整理事实记忆失败, groupId=$groupId", e)
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
                transaction {
                    existing.profile = analysis.profile
                    existing.preferences = analysis.preferences
                    log.info("用户画像已更新, botId=$botMark, groupId=$groupId, userId=$userId")
                }
            }

        } catch (e: Exception) {
            log.error("处理用户画像失败, groupId=$groupId, userId=$userId", e)
        }
    }

    /**
     * 处理事实记忆
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun processFacts(
        botMark: String,
        groupId: String,
        messages: List<MemoryAgent.MemoryMessage>
    ) {
        try {
            log.debug("开始提取事实记忆, groupId=$groupId")

            val existFacts = withContext(Dispatchers.IO) {
                memoryRepository.getValidFacts(botMark, groupId)
            }.map {
                MemoryAgent.FactsAnalysisInput(
                    id = it.id,
                    keyword = it.keyword,
                    description = it.description,
                    values = it.values.split(","),
                    subjects = it.subjects.split(","),
                    scopeType = when (it.scopeType) {
                        Scopes.USER -> MemoryAgent.MemoryScopes.USER
                        Scopes.GROUP -> MemoryAgent.MemoryScopes.GROUP
                    }
                )
            }

            log.debug("已存在 ${existFacts.size} 条事实记忆, groupId=$groupId")

            val factsList = memoryAgent.extractFacts(messages, existFacts)

            // 批量保存到数据库
            withContext(Dispatchers.IO) {
                var addCounted = 0
                var deprecateCounted = 0
                var mergeCounted = 0
                var updateCounted = 0
                for (fact in factsList) {
                    if (fact.confidence < 0.7) continue
                    when (fact.action) {
                        MemoryAgent.MemoryAction.ADD -> {
                            addCounted++
                            memoryRepository.createFact(
                                botMark = botMark,
                                groupId = groupId,
                                keyword = fact.keyword,
                                description = fact.description,
                                values = fact.values.joinToString(","),
                                subjects = fact.subjects.joinToString(","),
                                scopeType = when (fact.scopeType) {
                                    MemoryAgent.MemoryScopes.USER -> Scopes.USER
                                    MemoryAgent.MemoryScopes.GROUP -> Scopes.GROUP
                                }
                            )
                        }

                        MemoryAgent.MemoryAction.DEPRECATE -> {
                            deprecateCounted++
                            val existFact = existFacts.find {
                                it.id == fact.id || (it.keyword == fact.keyword &&
                                        it.subjects == fact.subjects &&
                                        it.scopeType == fact.scopeType)
                            }
                            if (existFact != null) {
                                memoryRepository.deprecateFacts(
                                    botMark = botMark,
                                    groupId = groupId,
                                    keyword = existFact.keyword,
                                    subjects = existFact.subjects.joinToString(","),
                                    scopeType = when (existFact.scopeType) {
                                        MemoryAgent.MemoryScopes.USER -> Scopes.USER
                                        MemoryAgent.MemoryScopes.GROUP -> Scopes.GROUP
                                    }
                                )
                            }
                        }

                        MemoryAgent.MemoryAction.MERGE -> {
                            mergeCounted++
                            // 合并：废弃被合并的事实
                            fact.id.let { id ->
                                val existFact = existFacts.find { it.id == id }
                                existFact?.let {
                                    memoryRepository.deprecateFacts(
                                        botMark = botMark,
                                        groupId = groupId,
                                        keyword = it.keyword,
                                        subjects = it.subjects.joinToString(","),
                                        scopeType = when (it.scopeType) {
                                            MemoryAgent.MemoryScopes.USER -> Scopes.USER
                                            MemoryAgent.MemoryScopes.GROUP -> Scopes.GROUP
                                        }
                                    )
                                }
                            }
                        }

                        MemoryAgent.MemoryAction.UPDATE -> {
                            updateCounted++
                            // 更新：先废弃旧事实，再添加新事实
                            fact.id.let { id ->
                                val existFact = existFacts.find { it.id == id }
                                existFact?.let {
                                    memoryRepository.deprecateFacts(
                                        botMark = botMark,
                                        groupId = groupId,
                                        keyword = it.keyword,
                                        subjects = it.subjects.joinToString(","),
                                        scopeType = when (it.scopeType) {
                                            MemoryAgent.MemoryScopes.USER -> Scopes.USER
                                            MemoryAgent.MemoryScopes.GROUP -> Scopes.GROUP
                                        }
                                    )
                                    memoryRepository.createFact(
                                        botMark = botMark,
                                        groupId = groupId,
                                        keyword = fact.keyword,
                                        description = fact.description,
                                        values = fact.values.joinToString(","),
                                        subjects = fact.subjects.joinToString(","),
                                        scopeType = when (fact.scopeType) {
                                            MemoryAgent.MemoryScopes.USER -> Scopes.USER
                                            MemoryAgent.MemoryScopes.GROUP -> Scopes.GROUP
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                log.info("事实记忆分析完成, botId=$botMark, groupId=$groupId, size=${factsList.size}, add=$addCounted, deprecate=$deprecateCounted, merge=$mergeCounted, update=$updateCounted")
            }

            // 更新向量索引
            updateFactVectorIndex(botMark, groupId, factsList, existFacts)
        } catch (e: Exception) {
            log.error("提取事实记忆失败, groupId=$groupId", e)
        }
    }

    /**
     * 更新事实向量索引
     */
    private suspend fun updateFactVectorIndex(
        botMark: String,
        groupId: String,
        factsList: List<MemoryAgent.FactsAnalysis>,
        existFacts: List<MemoryAgent.FactsAnalysisInput>
    ) {
        try {
            for (fact in factsList) {
                if (fact.confidence < 0.7) continue

                when (fact.action) {
                    MemoryAgent.MemoryAction.ADD -> {
                        // 获取刚创建的事实并索引
                        val newFact = withContext(Dispatchers.IO) {
                            memoryRepository.getLatestFact(botMark, groupId, fact.keyword, fact.scopeType)
                        }
                        newFact?.let {
                            val vectorId = factVectorStore.indexFact(it)
                            // 保存 vectorId 到数据库
                            withContext(Dispatchers.IO) {
                                memoryRepository.updateFactVectorId(it.id, vectorId)
                            }
                            log.debug("事实向量索引已创建, factId=${it.id}, vectorId=$vectorId")
                        }
                    }

                    MemoryAgent.MemoryAction.DEPRECATE -> {
                        // 找到被废弃的事实并删除向量索引
                        val deprecatedFact = existFacts.find {
                            it.id == fact.id || (it.keyword == fact.keyword &&
                                    it.subjects == fact.subjects &&
                                    it.scopeType == fact.scopeType)
                        }
                        deprecatedFact?.let {
                            val factEntity = withContext(Dispatchers.IO) {
                                memoryRepository.getFactByKeywordAndSubjects(
                                    botMark, groupId,
                                    it.keyword, it.subjects.joinToString(","),
                                    it.scopeType
                                )
                            }
                            factEntity?.let { entity ->
                                entity.vectorId?.let { vectorId ->
                                    factVectorStore.deleteVector(vectorId, botMark, groupId)
                                    log.debug("事实向量索引已删除, factId=${entity.id}, vectorId=$vectorId")
                                }
                            }
                        }
                    }

                    MemoryAgent.MemoryAction.MERGE, MemoryAgent.MemoryAction.UPDATE -> {
                        // 合并和更新的处理：先删除旧向量，再创建新向量
                        fact.id.let { id ->
                            val oldFact = existFacts.find { it.id == id }
                            oldFact?.let {
                                val factEntity = withContext(Dispatchers.IO) {
                                    memoryRepository.getFactByKeywordAndSubjects(
                                        botMark, groupId,
                                        it.keyword, it.subjects.joinToString(","),
                                        it.scopeType
                                    )
                                }
                                factEntity?.let { entity ->
                                    // 删除旧向量
                                    entity.vectorId?.let { vectorId ->
                                        factVectorStore.deleteVector(vectorId, botMark, groupId)
                                    }
                                }
                            }
                        }
                        // 如果是 UPDATE，还需要创建新向量
                        if (fact.action == MemoryAgent.MemoryAction.UPDATE) {
                            val newFact = withContext(Dispatchers.IO) {
                                memoryRepository.getLatestFact(botMark, groupId, fact.keyword, fact.scopeType)
                            }
                            newFact?.let {
                                val vectorId = factVectorStore.indexFact(it)
                                withContext(Dispatchers.IO) {
                                    memoryRepository.updateFactVectorId(it.id, vectorId)
                                }
                                log.debug("事实向量索引已更新, factId=${it.id}, vectorId=$vectorId")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            log.error("更新事实向量索引失败, groupId=$groupId", e)
        }
    }

    /**
     * 处理对话摘要
     */
    private suspend fun processSummary(
        botMark: String,
        groupId: String,
        messages: List<MemoryAgent.MemoryMessage>
    ) {
        try {
            log.debug("开始生成对话摘要, scopeId=$groupId")

            val summary = memoryAgent.generateSummary(messages, groupId)

            // 保存到数据库
            memoryRepository.saveSummary(
                botMark = botMark,
                groupId = groupId,
                timeRange = summary.timeRange,
                content = summary.content,
                keyPoints = summary.keyPoints.joinToString("\n"),
                emotionalTone = summary.emotionalTone,
                participantCount = summary.participantIds.distinct().size,
                messageCount = summary.messageCount
            )
            log.info("对话摘要分析完成, botId=$botMark, groupId=$groupId")

        } catch (e: Exception) {
            log.error("生成对话摘要失败, groupId=$groupId", e)
        }
    }

    // ====== 原有查询方法 ======

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