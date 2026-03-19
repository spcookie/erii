package uesugi.core.state.flow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.jobrunr.scheduling.JobScheduler
import org.koin.core.context.GlobalContext
import uesugi.BotManage
import uesugi.common.ENABLE_GROUPS
import uesugi.common.logger
import kotlin.time.ExperimentalTime

class FlowJob(
    val jobScheduler: JobScheduler,
    private val flowAgent: FlowAgent,
    private val flowRepository: FlowRepository
) {

    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()

    fun openTimingTriggerSignal() {
        for (group in ENABLE_GROUPS) {
            for (bot in BotManage.getAllBotIds()) {
                log.info("init flow for bot $bot in group $group")
                ensureFlowGaugeExists(bot, group)
            }
        }
        jobScheduler.scheduleRecurrently(
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
                            flowRepository.findGroupsNeedProcessing(currentBotId)
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
        flowGaugeManager.getOrCreate(botMark, groupId, BotManage.getBot(botMark).role.emoticon)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun processGroupFlow(botMark: String, groupId: String) {
        log.debug("开始处理群组心流, groupId=$groupId")

        try {
            val flowState = flowRepository.getFlowState(botMark, groupId)
            val lastId = flowState?.lastProcessedHistoryId ?: 0

            val histories = withContext(Dispatchers.IO) {
                flowRepository.getHistoriesToProcess(botMark, groupId, lastId, 100)
            }

            if (histories.size < 20) {
                log.debug("群组 $groupId 新消息不足20条, 跳过处理心流")
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
                log.debug("群组 $groupId 消息转换后为空, 跳过处理")
                val maxHistoryId = histories.maxOf { it.id.value }
                flowRepository.updateFlowState(botMark, groupId, maxHistoryId)
                return
            }

            flowAgent.analysis(messages, botMark, groupId)

            val maxHistoryId = histories.maxOf { it.id.value }
            flowRepository.updateFlowState(botMark, groupId, maxHistoryId)

            log.debug("群组 $groupId 心流处理完成, 最大 historyId=$maxHistoryId")

        } catch (e: Exception) {
            log.error("处理群组 $groupId 心流失败", e)
        }
    }

}