package uesugi.core.flow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.scheduling.BackgroundJob
import org.koin.core.context.GlobalContext
import uesugi.BotManage
import uesugi.core.history.HistoryEntity
import uesugi.core.history.HistoryTable
import uesugi.toolkit.logger
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class FlowJob {

    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()

    private val flowAgent = FlowAgent()

    fun openTimingTriggerSignal() {
        BackgroundJob.scheduleRecurrently(
            "flow-job",
            "*/1 * * * *",
            ::doFlowAnalysis
        )
        log.info("心流任务定时器已启动, 执行周期: 每分钟")
    }

    fun doFlowAnalysis() {
        runBlocking {
            if (mutex.tryLock()) {
                try {
                    log.debug("心流任务开始执行")

                    for (currentBotId in BotManage.getAllBotIds()) {
                        log.debug("开始处理心流任务: currentBotId=$currentBotId")

                        val groups = withContext(Dispatchers.IO) {
                            transaction {
                                findGroupsNeedProcessing(currentBotId)
                            }
                        }

                        log.debug("心流任务发现 ${groups.size} 个群组需要处理")

                        for (groupId in groups) {
                            ensureFlowGaugeExists(currentBotId, groupId)
                            processGroupFlow(currentBotId, groupId)
                        }
                    }

                    log.debug("心流任务执行完成")
                } catch (e: Exception) {
                    log.error("心流任务执行失败", e)
                } finally {
                    mutex.unlock()
                }
            } else {
                log.debug("心流任务正在执行中, 跳过本次调度")
            }
        }
    }

    private fun ensureFlowGaugeExists(botMark: String, groupId: String) {
        val flowGaugeManager by GlobalContext.get().inject<FlowGaugeManager>()
        flowGaugeManager.getOrCreate(botMark, groupId, BotManage.getBot(botMark)!!.role.emoticon)
    }

    private fun findGroupsNeedProcessing(botMark: String): List<String> {
        return transaction {
            val allGroupIds = HistoryTable
                .select(HistoryTable.groupId)
                .where { HistoryTable.botMark eq botMark }
                .groupBy(HistoryTable.groupId)
                .map { it[HistoryTable.groupId] }
                .distinct()

            allGroupIds.filter { groupId ->
                val flowState = FlowStateEntity.find {
                    (FlowStateTable.botMark eq botMark) and (FlowStateTable.groupId eq groupId)
                }.firstOrNull()

                val lastProcessedId = flowState?.lastProcessedHistoryId ?: 0

                val newMessageCount = HistoryEntity.count(
                    HistoryTable.botMark eq botMark and
                            (HistoryTable.groupId eq groupId) and
                            (HistoryTable.id greater lastProcessedId)
                )

                newMessageCount > 3
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun processGroupFlow(botMark: String, groupId: String) {
        log.debug("开始处理群组心流, groupId=$groupId")

        try {
            val histories = withContext(Dispatchers.IO) {
                transaction {
                    val flowState = FlowStateEntity.find {
                        (FlowStateTable.botMark eq botMark) and (FlowStateTable.groupId eq groupId)
                    }.firstOrNull()

                    val lastId = flowState?.lastProcessedHistoryId ?: 0

                    val historyList = HistoryEntity.find {
                        (HistoryTable.botMark eq botMark) and
                                (HistoryTable.groupId eq groupId) and
                                (HistoryTable.id greater lastId)
                    }
                        .orderBy(HistoryTable.createdAt to SortOrder.ASC)
                        .limit(100)
                        .toList()

                    historyList
                }
            }

            if (histories.isEmpty()) {
                log.debug("群组 $groupId 没有新消息需要处理")
                return
            }

            log.debug("群组 $groupId 获取到 ${histories.size} 条新消息")

            val messages = histories.map {
                FlowMessage(
                    id = it.id.value,
                    groupId = it.groupId,
                    userId = it.userId,
                    time = it.createdAt,
                    content = it.content ?: ""
                )
            }

            if (messages.isEmpty()) {
                log.debug("群组 $groupId 消息转换后为空,跳过处理")
                updateFlowState(botMark, groupId, histories.maxOf { it.id.value })
                return
            }

            flowAgent.analysis(messages, botMark, groupId)

            val maxHistoryId = histories.maxOf { it.id.value }
            updateFlowState(botMark, groupId, maxHistoryId)

            log.debug("群组 $groupId 心流处理完成, 最大 historyId=$maxHistoryId")

        } catch (e: Exception) {
            log.error("处理群组 $groupId 心流失败", e)
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun updateFlowState(botMark: String, groupId: String, lastHistoryId: Int) {
        withContext(Dispatchers.IO) {
            transaction {
                val existing = FlowStateEntity.find {
                    (FlowStateTable.botMark eq botMark) and (FlowStateTable.groupId eq groupId)
                }.firstOrNull()

                val now = Clock.System.now()
                val tz = TimeZone.currentSystemDefault()
                val instant = now.toLocalDateTime(tz)

                if (existing != null) {
                    existing.lastProcessedHistoryId = lastHistoryId
                    existing.lastProcessedAt = instant
                } else {
                    FlowStateEntity.new {
                        this.botMark = botMark
                        this.groupId = groupId
                        this.lastProcessedHistoryId = lastHistoryId
                        this.lastProcessedAt = instant
                    }
                }

                log.debug("心流状态已更新, groupId=$groupId, lastHistoryId=$lastHistoryId")
            }
        }
    }
}