package uesugi.core.evolution

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.scheduling.BackgroundJob
import uesugi.BotManage
import uesugi.core.history.HistoryTable
import uesugi.toolkit.logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

class EvolutionJob(
    private val vocabularyService: VocabularyService
) {
    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()
    private val extractionAgent = ExtractionAgent()

    /**
     * 开启定时触发
     *
     * 每 1 小时执行一次模因进化处理
     */
    fun openTimingTriggerSignal() {
        BackgroundJob.scheduleRecurrently(
            "evolution-job",
            "0 */1 * * *",  // 每 1 小时一次
            ::doEvolutionProcessing
        )
        log.info("模因进化任务定时器已启动, 执行周期: 每小时")
    }

    /**
     * 执行模因进化处理
     *
     * 主流程：
     * 1. 获取所有活跃群组
     * 2. 逐个群组处理：捕获消息 -> 提取流行语 -> 更新词汇库 -> 热度衰减
     */
    fun doEvolutionProcessing() {
        runBlocking {
            if (mutex.tryLock()) {
                try {
                    log.debug("模因进化任务开始执行")
                    for (currentBotId in BotManage.getAllBotIds()) {
                        log.debug("开始模因进化任务: currentBotId=$currentBotId")

                        val groups = getActiveGroups(currentBotId)

                        log.debug("发现 ${groups.size} 个活跃群组需要处理")

                        for (groupId in groups) {
                            processGroupEvolution(currentBotId, groupId, 1.hours)
                        }
                    }

                    log.debug("模因进化任务执行完成")
                } catch (e: Exception) {
                    log.error("模因进化任务执行失败", e)
                } finally {
                    mutex.unlock()
                }
            } else {
                log.debug("模因进化任务正在执行中, 跳过本次调度")
            }
        }
    }

    /**
     * 获取活跃群组列表
     *
     * 活跃群组定义：最近有消息记录的群组
     *
     * @param botMark 机器人标识
     * @return 群组ID列表
     */
    private fun getActiveGroups(botMark: String): List<String> {
        return transaction {
            log.debug("开始查询活跃群组, botId=$botMark")

            val groups = HistoryTable
                .select(HistoryTable.groupId)
                .where { HistoryTable.botMark eq botMark }
                .groupBy(HistoryTable.groupId)
                .map { it[HistoryTable.groupId] }
                .distinct()

            log.debug("查询到活跃群组数量: ${groups.size}")
            groups
        }
    }

    /**
     * 处理单个群组的模因进化
     *
     * 处理流程：
     * 1. 捕获最近 500 条活跃消息
     * 2. 使用 LLM 提取流行语
     * 3. 更新词汇库（新增或增加热度）
     * 4. 执行热度衰减（遗忘过气的梗）
     *
     * @param botMark 机器人标识
     * @param groupId 群组ID
     */
    private suspend fun processGroupEvolution(botMark: String, groupId: String, range: Duration) {
        log.debug("开始处理群组模因进化, 群组ID: $groupId")

        try {
            // 步骤1: 捕获最近的消息
            val recentMessages = vocabularyService.getMostActiveMessages(botMark, groupId, 500, range)

            if (recentMessages.isEmpty()) {
                log.warn("群组 $groupId 没有最近的消息，跳过处理")
                return
            }

            log.debug("消息捕获完成, 消息数=${recentMessages.size}")

            // 步骤2: 使用 LLM 提取流行语
            val slangWords = extractionAgent.extractSlangWords(recentMessages)

            if (slangWords.isEmpty()) {
                log.warn("未提取到流行语")
            } else {
                log.debug("流行语提取完成, 提取数量=${slangWords.size}")
                slangWords.forEachIndexed { index, slang ->
                    log.debug("  ${index + 1}. ${slang.word} (${slang.type}) - ${slang.meaning}")
                }
            }

            // 步骤3: 更新词汇库
            for (slangWord in slangWords) {
                vocabularyService.addOrUpdateWord(botMark, groupId, slangWord)
            }
            log.debug("词汇库更新完成")

            // 步骤4: 热度衰减
            vocabularyService.decayOldWords(botMark, groupId, recentMessages)
            log.debug("热度衰减完成")

            log.debug("群组 $groupId 模因进化处理完成")

        } catch (e: Exception) {
            log.error("处理群组 $groupId 模因进化失败", e)
        }
    }
}