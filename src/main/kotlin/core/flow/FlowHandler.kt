package uesugi.core.flow

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import uesugi.core.history.HistorySavedEvent
import uesugi.toolkit.EventBus
import uesugi.toolkit.logger
import kotlin.time.Duration.Companion.minutes

class FlowHandler(
    private val flowGauge: FlowGauge
) {

    companion object {
        private val log = logger()
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    fun start() {
        val flowAgent = FlowAgent(
            """
                兴趣爱好：
                小说、轻小说、动漫、二次元游戏
                文学、哲学思考、历史趣闻
                科技资讯、群聊段子
                
                常聊话题：
                剧情分析或讨论
                群友趣事、吐槽
                网络梗、段子、轻幽默
                偶尔哲理或人生感悟
            """.trimIndent()
        )

        val channel = Channel<FlowMessage>(1000, BufferOverflow.DROP_OLDEST)

        scope.launch {
            val result = mutableListOf<FlowMessage>()
            while (true) {
                delay(1.minutes)
                while (true) {
                    val r = channel.tryReceive()
                    if (r.isSuccess) {
                        result += r.getOrThrow()
                        if (result.size > 100) {
                            break
                        }
                    } else {
                        break
                    }
                }
                if (result.size > 5) {
                    flowAgent.analysis(result)
                    result.clear()
                }
            }
        }

        EventBus.subscribeAsync(HistorySavedEvent::class, scope) { event ->
            val historyEntity = event.historyEntity
            val message = FlowMessage(
                id = historyEntity.id.value,
                userId = historyEntity.userId,
                time = historyEntity.createdAt,
                content = historyEntity.content ?: ""
            )
            channel.send(message)
        }

    }

    fun close() {
        try {
            flowGauge.stop()
            scope.cancel()
        } catch (e: Exception) {
            log.warn("Error closing FlowHandler", e)
        }
    }

}