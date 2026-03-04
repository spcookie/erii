package uesugi.core.state.memory

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.datetime.CurrentDateTime
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jobrunr.scheduling.JobScheduler
import uesugi.BotManage
import uesugi.core.message.history.HistoryEntity
import uesugi.core.message.history.HistoryTable
import uesugi.toolkit.logger
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * 记忆任务 - 定时从历史消息中提取和生成各类记忆数据
 */
class MemoryJob(
    val jobScheduler: JobScheduler
) {

    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()
    private val memoryAgent = MemoryAgent()

    /**
     * 开启定时触发
     * 每 5 分钟执行一次记忆处理
     */
    fun openTimingTriggerSignal() {
        jobScheduler.scheduleRecurrently(
            "memory-job",
            "*/5 * * * *",  // 每 5 分钟
            ::doMemoryProcessing
        )
        log.info("记忆任务定时器已启动, 执行周期: 每5分钟")
    }

    /**
     * 执行记忆处理
     */
    fun doMemoryProcessing() {
        runBlocking {
            if (mutex.tryLock()) {
                try {
                    log.debug("记忆任务开始执行")

                    for (currentBotId in BotManage.getAllBotIds()) {
                        log.debug("开始处理机器人 $currentBotId 的记忆")

                        // 查找需要处理的群组
                        val groups = withContext(Dispatchers.IO) {
                            transaction {
                                findGroupsNeedProcessing(currentBotId)
                            }
                        }

                        log.debug("记忆任务发现 ${groups.size} 个群组需要处理")

                        // 逐个群组处理
                        for (groupId in groups) {
                            processGroupMemory(currentBotId, groupId)
                        }
                    }

                    log.debug("记忆任务执行完成")
                } catch (e: Exception) {
                    log.error("记忆任务执行失败", e)
                } finally {
                    mutex.unlock()
                }
            } else {
                log.debug("记忆任务正在执行中, 跳过本次调度")
            }
        }
    }

    /**
     * 查找需要处理记忆的群组
     * 规则: 自上次处理后有新消息的群组
     */
    private fun findGroupsNeedProcessing(botMark: String): List<String> {
        return transaction {
            // 查询所有群组的最新消息 ID
            val allGroupIds = HistoryTable
                .select(HistoryTable.groupId)
                .where { HistoryTable.botMark eq botMark }
                .groupBy(HistoryTable.groupId)
                .map { it[HistoryTable.groupId] }
                .distinct()

            // 过滤出有新消息的群组
            allGroupIds.filter { groupId ->
                val memoryState = MemoryStateEntity.find {
                    (MemoryStateTable.botMark eq botMark) and (MemoryStateTable.groupId eq groupId)
                }.firstOrNull()

                val lastProcessedId = memoryState?.lastProcessedHistoryId ?: 0

                // 检查是否有新消息
                val newMessageCount = HistoryEntity.count(
                    HistoryTable.botMark eq botMark and
                            (HistoryTable.groupId eq groupId) and
                            (HistoryTable.id greater lastProcessedId)
                )

                newMessageCount > 0
            }
        }
    }

    /**
     * 处理单个群组的记忆
     */
    private suspend fun processGroupMemory(botMark: String, groupId: String) {
        log.debug("开始处理群组记忆, groupId=$groupId")

        try {
            // 1. 获取需要处理的历史消息
            val histories = withContext(Dispatchers.IO) {
                transaction {
                    val memoryState = MemoryStateEntity.find {
                        (MemoryStateTable.botMark eq botMark) and (MemoryStateTable.groupId eq groupId)
                    }.firstOrNull()

                    val lastId = memoryState?.lastProcessedHistoryId ?: 0

                    val historyList = HistoryEntity.find {
                        (HistoryTable.botMark eq botMark) and
                                (HistoryTable.groupId eq groupId) and
                                (HistoryTable.id greater lastId)
                    }
                        .orderBy(HistoryTable.createdAt to SortOrder.ASC)
                        .limit(200)  // 每次最多处理 200 条
                        .toList()

                    historyList
                }
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
                updateMemoryState(botMark, groupId, histories.maxOf { it.id.value })
                return
            }

            // 3. 按用户分组
            val messagesByUser = messages.groupBy { it.userId }

            // 4. 并发处理各类记忆生成
            coroutineScope {
                // 4.1 用户画像和偏好 (按用户)
                launch {
                    for ((userId, userMessages) in messagesByUser) {
                        if (userId != botMark) {  // 至少 5 条消息才分析
                            processUserProfile(botMark, groupId, userId, userMessages)
                        }
                    }
                }

                // 4.2 事实记忆提取
                launch {
                    processFacts(botMark, groupId, messages)
                }

                // 4.３ 对话摘要生成
                launch {
                    processSummary(botMark, groupId, messages)
                }
            }

            // 5. 更新记忆处理状态
            val maxHistoryId = histories.maxOf { it.id.value }
            updateMemoryState(botMark, groupId, maxHistoryId)

            log.debug("群组 $groupId 记忆处理完成, 最大 historyId=$maxHistoryId")

        } catch (e: Exception) {
            log.error("处理群组 $groupId 记忆失败", e)
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
                transaction {
                    // 查找或创建用户画像
                    UserProfileEntity.find {
                        (UserProfileTable.botMark eq botMark) and
                                (UserProfileTable.groupId eq groupId) and
                                (UserProfileTable.userId eq userId)
                    }.firstOrNull()
                }
            }
            val analysis = memoryAgent.analyzeUserProfile(messages, existing)

            // 保存到数据库
            withContext(Dispatchers.IO) {
                transaction {
                    if (existing != null) {
                        // 更新现有画像
                        existing.profile = analysis.profile
                        existing.preferences = analysis.preferences
                        log.info("用户画像已更新, botId=$botMark, groupId=$groupId, userId=$userId, profile=${analysis.profile}, preferences=${analysis.preferences}")
                    } else {
                        // 创建新画像
                        UserProfileEntity.new {
                            this.botMark = botMark
                            this.groupId = groupId
                            this.userId = userId
                            this.profile = analysis.profile
                            this.preferences = analysis.preferences
                        }
                        log.info("用户画像已创建, botId=$botMark, groupId=$groupId, userId=$userId, profile=${analysis.profile}, preferences=${analysis.preferences}")
                    }
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
                transaction {
                    FactsEntity.find {
                        (FactsTable.botMark eq botMark) and
                                (FactsTable.groupId eq groupId) and
                                (FactsTable.validFrom lessEq CurrentDateTime) and
                                (FactsTable.validTo greater CurrentDateTime)
                    }.toList()
                }
            }.map {
                MemoryAgent.FactsAnalysisInput(
                    id = it.id.value,
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
                transaction {
                    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                    var addCounted = 0
                    var deprecateCounted = 0
                    for (fact in factsList) {
                        if (fact.confidence < 0.7) continue
                        when (fact.action) {
                            MemoryAgent.MemoryAction.ADD -> {
                                addCounted++
                                FactsEntity.new {
                                    this.botMark = botMark
                                    this.groupId = groupId
                                    keyword = fact.keyword
                                    description = fact.description
                                    values = fact.values.joinToString(",")
                                    subjects = fact.subjects.joinToString(",")
                                    scopeType = when (fact.scopeType) {
                                        MemoryAgent.MemoryScopes.USER -> Scopes.USER
                                        MemoryAgent.MemoryScopes.GROUP -> Scopes.GROUP
                                    }
                                    validFrom = now
                                    validTo = null
                                }
                            }

                            MemoryAgent.MemoryAction.DEPRECATE -> {
                                deprecateCounted++
                                existFacts.filter {
                                    it.id == fact.id || (it.keyword == fact.keyword &&
                                            it.subjects == fact.subjects &&
                                            it.scopeType == fact.scopeType)
                                }.forEach { exist ->
                                    FactsTable.update({
                                        (FactsTable.botMark eq botMark) and
                                                (FactsTable.groupId eq groupId) and
                                                (FactsTable.keyword eq exist.keyword) and
                                                (FactsTable.subjects eq exist.subjects.joinToString(",")) and
                                                (FactsTable.scopeType eq when (exist.scopeType) {
                                                    MemoryAgent.MemoryScopes.USER -> Scopes.USER
                                                    MemoryAgent.MemoryScopes.GROUP -> Scopes.GROUP
                                                })
                                    }) {
                                        it[FactsTable.validTo] = now
                                    }
                                }
                            }
                        }
                    }
                    log.info("事实记忆分析完成, botId=$botMark, groupId=$groupId, size=${factsList.size}, add=${addCounted}, deprecate=${deprecateCounted}")
                }
            }
        } catch (e: Exception) {
            log.error("提取事实记忆失败, groupId=$groupId", e)
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
            withContext(Dispatchers.IO) {
                transaction {
                    SummaryEntity.new {
                        this.botMark = botMark
                        this.groupId = groupId
                        timeRange = summary.timeRange
                        content = summary.content
                        keyPoints = summary.keyPoints.joinToString("\n")
                        emotionalTone = summary.emotionalTone
                        participantCount = summary.participantIds.distinct().size
                        messageCount = summary.messageCount
                    }
                    log.info("对话摘要分析完成, botId=$botMark, groupId=$groupId, summary=${summary.content}")
                }
            }

        } catch (e: Exception) {
            log.error("生成对话摘要失败, groupId=$groupId", e)
        }
    }

    /**
     * 更新记忆处理状态
     */
    @OptIn(ExperimentalTime::class)
    private suspend fun updateMemoryState(botMark: String, groupId: String, lastHistoryId: Int) {
        withContext(Dispatchers.IO) {
            transaction {
                val existing = MemoryStateEntity.find {
                    (MemoryStateTable.botMark eq botMark) and (MemoryStateTable.groupId eq groupId)
                }.firstOrNull()

                val now = Clock.System.now()
                val tz = TimeZone.currentSystemDefault()
                val instant = now.toLocalDateTime(tz)

                if (existing != null) {
                    existing.lastProcessedHistoryId = lastHistoryId
                    existing.lastProcessedAt = instant
                } else {
                    MemoryStateEntity.new {
                        this.botMark = botMark
                        this.groupId = groupId
                        this.lastProcessedHistoryId = lastHistoryId
                        this.lastProcessedAt = instant
                    }
                }

                log.debug("记忆状态已更新, groupId=$groupId, lastHistoryId=$lastHistoryId")
            }
        }
    }
}