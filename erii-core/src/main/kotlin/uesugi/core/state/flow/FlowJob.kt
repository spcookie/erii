package uesugi.core.state.flow

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import uesugi.common.BotManage
import uesugi.common.data.HistoryRecord
import uesugi.common.data.MessageType
import uesugi.common.toolkit.ConfigHolder
import uesugi.common.toolkit.logger
import uesugi.core.component.usage.UsageContext
import uesugi.core.state.dispatch.*
import kotlin.time.ExperimentalTime

class FlowJob(
    private val flowAgent: FlowAgent,
    private val flowRepository: FlowRepository
) : StateWorkProcessor {

    override val kind = StateWorkKind.FLOW

    companion object {
        private val log = logger()
    }

    fun openTimingTriggerSignal() {
        for (bot in BotManage.getAllBotIds()) {
            val configKey = BotManage.getConfigKey(bot)
            for (group in ConfigHolder.getEffectiveEnableGroups(configKey)) {
                log.info("init flow for bot $bot in group $group")
                ensureFlowGaugeExists(bot, group)
            }
        }
        log.info("Flow event processor initialized")
    }

    override fun accepts(record: HistoryRecord): Boolean = record.messageType == MessageType.TEXT

    override fun pendingKeys(): Set<StateWorkKey> = buildSet {
        for (botId in BotManage.getAllBotIds()) {
            flowRepository.findGroupsNeedProcessing(botId).forEach { groupId ->
                add(StateWorkKey(botId, groupId, kind))
            }
        }
    }

    override suspend fun process(
        key: StateWorkKey,
        policy: StateWorkPolicy,
        force: Boolean
    ): StateWorkResult {
        ensureFlowGaugeExists(key.botId, key.groupId)
        return UsageContext.withUsage(key.botId, key.groupId) {
            processGroupFlow(key.botId, key.groupId, policy, force)
        }
    }

    private fun ensureFlowGaugeExists(botMark: String, groupId: String) {
        val flowGaugeManager by GlobalContext.get().inject<FlowGaugeManager>()
        val configKey = BotManage.getConfigKey(botMark)
        val baseDesire = ConfigHolder.getOnebotBots()[configKey]?.groups?.get(groupId)?.desire
            ?: ConfigHolder.getStateTuning().volition.baseDesireDefault
        flowGaugeManager.getOrCreate(botMark, groupId, BotManage.getBot(botMark).role.emoticon, baseDesire)
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun processGroupFlow(
        botMark: String,
        groupId: String,
        policy: StateWorkPolicy,
        force: Boolean
    ): StateWorkResult {
        log.debug("开始处理群组心流, groupId=$groupId")

        try {
            val flowState = withContext(Dispatchers.IO) {
                flowRepository.getFlowState(botMark, groupId)
            }
            val lastId = flowState?.lastProcessedHistoryId ?: 0

            val histories = withContext(Dispatchers.IO) {
                flowRepository.getLatestHistoriesToProcess(botMark, groupId, lastId, policy.batchLimit)
            }

            if (histories.isEmpty() || (!force && histories.size < policy.minMessages)) {
                return StateWorkResult(0, lastId, hasMore = false)
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
                withContext(Dispatchers.IO) {
                    flowRepository.updateFlowState(botMark, groupId, maxHistoryId)
                }
                return StateWorkResult(histories.size, maxHistoryId, hasMore = false)
            }

            flowAgent.analysis(messages, botMark, groupId)

            val maxHistoryId = histories.maxOf { it.id.value }
            withContext(Dispatchers.IO) {
                flowRepository.updateFlowState(botMark, groupId, maxHistoryId)
            }

            log.debug("群组 $groupId 心流处理完成, 最大 historyId=$maxHistoryId")
            return StateWorkResult(histories.size, maxHistoryId, hasMore = false)

        } catch (e: Exception) {
            log.error("处理群组 $groupId 心流失败", e)
            throw e
        }
    }

}
