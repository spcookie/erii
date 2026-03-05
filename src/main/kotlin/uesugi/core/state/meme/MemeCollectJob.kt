package uesugi.core.state.meme

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jobrunr.scheduling.BackgroundJob
import uesugi.BotManage
import uesugi.ENABLE_GROUPS
import uesugi.core.message.history.HistoryTable
import uesugi.core.message.history.MessageType
import uesugi.toolkit.logger

/**
 * 表情包收集 Job
 *
 * 定时扫描历史消息中的图片：
 * 1. 如果 md5 已存在，追加上下文，计数+1
 * 2. 如果 md5 不存在，创建新记录
 * 3. 累计计数达到3后，标记待分析
 *
 * 使用扫描状态表记录最后扫描的 history id，避免重复扫描
 */
class MemeCollectJob(
    private val memeService: MemoService
) {
    companion object {
        private val log = logger()
    }

    private val mutex = Mutex()

    /**
     * 开启定时触发
     *
     * 每 30 分钟执行一次表情包收集
     */
    fun openTimingTriggerSignal() {
        BackgroundJob.scheduleRecurrently(
            "meme-collect-job",
            "0,30 * * * *",  // 每 30 分钟一次
            ::doCollecting
        )
        log.info("表情包收集任务已启动 执行周期: 30分钟")
    }

    /**
     * 执行表情包收集
     */
    fun doCollecting() {
        runBlocking {
            if (mutex.tryLock()) {
                try {
                    log.debug("表情包收集任务开始执行")

                    val groups = ENABLE_GROUPS
                    log.debug("需要收集的群组: ${groups.size} 个")

                    for (groupId in groups) {
                        for (botId in BotManage.getAllBotIds()) {
                            processGroupCollection(botId, groupId)
                        }
                    }

                    log.debug("表情包收集任务执行完成")
                } catch (e: Exception) {
                    log.error("表情包收集任务执行失败", e)
                } finally {
                    mutex.unlock()
                }
            } else {
                log.debug("表情包收集任务正在执行中, 跳过本次调度")
            }
        }
    }

    /**
     * 处理单个群组的表情包收集
     */
    private fun processGroupCollection(botId: String, groupId: String) {
        log.debug("开始收集群组表情包, 群组ID: $groupId")

        try {
            // 获取扫描状态（上次扫描到的最后 history id）
            val scanState = memeService.getScanState(botId, groupId)
            val lastHistoryId = scanState?.lastHistoryId ?: 0
            log.debug("上次扫描到的最后 history id: $lastHistoryId")

            // 获取增量图片消息（只获取 id > lastHistoryId 的）
            val newImages = memeService.getRecentImageMessages(
                botId = botId,
                groupId = groupId,
                lastHistoryId = lastHistoryId,
                limit = 500
            )

            if (newImages.isEmpty()) {
                log.debug("群组 $groupId 没有新的图片消息需要处理")
                return
            }

            log.debug("发现 ${newImages.size} 张新图片需要处理")

            var maxHistoryId = lastHistoryId

            for (image in newImages) {
                try {
                    // 更新最大 history id
                    if (image.historyId > maxHistoryId) {
                        maxHistoryId = image.historyId
                    }

                    if (image.md5.isBlank()) {
                        log.warn("图片 ${image.resourceId} 没有MD5，跳过")
                        continue
                    }

                    // 获取上下文
                    val context = getContextMessage(botId, groupId, image.historyId)

                    // 添加或更新表情包
                    memeService.addOrUpdateMeme(
                        botId = botId,
                        groupId = groupId,
                        resourceId = image.resourceId,
                        md5 = image.md5,
                        context = context
                    )

                    log.debug("处理图片: md5=${image.md5}, historyId=${image.historyId}")
                } catch (e: Exception) {
                    log.error("处理图片 ${image.resourceId} 失败", e)
                }
            }

            // 更新扫描状态
            if (maxHistoryId > lastHistoryId) {
                memeService.updateScanState(botId, groupId, maxHistoryId)
                log.debug("扫描状态已更新: lastHistoryId=$maxHistoryId")
            }

            log.debug("群组 $groupId 表情包收集完成")

        } catch (e: Exception) {
            log.error("处理群组 $groupId 表情包收集失败", e)
        }
    }

    /**
     * 获取指定图片消息前后的文本上下文
     * @param historyId 图片消息的 history id
     */
    private fun getContextMessage(botMark: String, groupId: String, historyId: Int): String? {
        return transaction {
            // 获取该图片之前的文本消息（id < historyId 的最近的消息）
            val beforeMessages = HistoryTable
                .select(HistoryTable.content)
                .where {
                    (HistoryTable.botMark eq botMark) and
                            (HistoryTable.groupId eq groupId) and
                            (HistoryTable.messageType eq MessageType.TEXT) and
                            (HistoryTable.id less historyId)
                }
                .orderBy(HistoryTable.id, SortOrder.DESC)
                .limit(10)
                .mapNotNull { it[HistoryTable.content] }
                .reversed() // 按时间正序

            // 获取该图片之后的文本消息（id > historyId 的最近的消息）
            val afterMessages = HistoryTable
                .select(HistoryTable.content)
                .where {
                    (HistoryTable.botMark eq botMark) and
                            (HistoryTable.groupId eq groupId) and
                            (HistoryTable.messageType eq MessageType.TEXT) and
                            (HistoryTable.id greater historyId)
                }
                .orderBy(HistoryTable.id, SortOrder.ASC)
                .limit(10)
                .mapNotNull { it[HistoryTable.content] }

            // 合并前后消息
            val allContexts = beforeMessages + afterMessages

            allContexts.joinToString("\n").takeIf { it.isNotBlank() }
        }
    }
}
