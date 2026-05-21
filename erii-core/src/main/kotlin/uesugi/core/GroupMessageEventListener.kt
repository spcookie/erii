package uesugi.core

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.mamoe.mirai.event.EventHandler
import net.mamoe.mirai.event.SimpleListenerHost
import net.mamoe.mirai.event.events.GroupAwareMessageEvent
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.GroupMessageSyncEvent
import net.mamoe.mirai.event.events.MessageEvent
import org.koin.core.context.GlobalContext
import uesugi.common.message.MessageContext
import uesugi.common.toolkit.ConfigHolder
import uesugi.common.toolkit.logger
import uesugi.core.message.pipeline.MessagePipeline
import uesugi.core.message.platform.MiraiMessagePlatformAdapter
import kotlin.coroutines.CoroutineContext

class GroupMessageEventListener(
    private val botId: String,
    private val roleName: String,
    private val botConfigKey: String
) : SimpleListenerHost() {

    companion object {
        private val log = logger()
    }

    private val effectiveEnableGroups by lazy { ConfigHolder.getEffectiveEnableGroups(botConfigKey) }
    private val effectiveRedirectMap by lazy { ConfigHolder.getEffectiveMessageRedirectMap(botConfigKey) }

    private val pipeline by GlobalContext.get().inject<MessagePipeline>()
    private val adapter = MiraiMessagePlatformAdapter()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val serial = mutableMapOf<String, Channel<GroupAwareMessageEvent>>()

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        log.error("Message listener exception", exception)
    }

    private fun channelKey(groupId: String) = "${botId}_$groupId"

    @EventHandler
    suspend fun MessageEvent.onMessage() {
        if (this !is GroupMessageSyncEvent && this !is GroupMessageEvent) return
        if (this.bot.id.toString() != botId) return

        serial.computeIfAbsent(channelKey(group.id.toString())) {
            val channel = Channel<GroupAwareMessageEvent>(Channel.UNLIMITED)
            scope.launch {
                for (event in channel) {
                    launch {
                        handleEvent(event)
                    }
                }
            }
            channel
        }.send(this)
    }

    suspend fun handleEvent(event: GroupAwareMessageEvent) {
        val rawGroupId = adapter.extractRawGroupId(event)
        val groupId = resolveGroupId(rawGroupId)
        if (groupId !in effectiveEnableGroups) return

        val context = MessageContext(
            botId = botId,
            groupId = groupId,
            senderId = adapter.extractSenderId(event),
            senderNick = adapter.extractSenderNick(event),
            parsedMessage = adapter.parseMessage(event, botId)
        )

        pipeline.process(context, roleName)
    }

    private fun resolveGroupId(rawGroupId: String): String {
        return effectiveRedirectMap.getOrDefault(rawGroupId, rawGroupId)
    }
}
