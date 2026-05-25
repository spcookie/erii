package uesugi.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import uesugi.common.message.MessageContext
import uesugi.common.toolkit.ConfigHolder
import uesugi.common.toolkit.logger
import uesugi.core.message.pipeline.MessagePipeline
import uesugi.core.message.platform.OneBotMessagePlatformAdapter
import uesugi.onebot.core.model.GroupMessageEvent
import uesugi.onebot.sdk.client.OneBotClient
import uesugi.onebot.sdk.client.onGroupMessage

class GroupMessageEventListener(
    private val botId: String,
    private val roleName: String,
    private val botConfigKey: String
) {

    companion object {
        private val log = logger()
    }

    private val effectiveEnableGroups by lazy { ConfigHolder.getEffectiveEnableGroups(botConfigKey) }
    private val effectiveRedirectMap by lazy { ConfigHolder.getEffectiveMessageRedirectMap(botConfigKey) }

    private val pipeline by GlobalContext.get().inject<MessagePipeline>()
    private val adapter = OneBotMessagePlatformAdapter()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val serial = mutableMapOf<String, Channel<GroupMessageEvent>>()

    private fun channelKey(groupId: String) = "${botId}_$groupId"

    fun register(client: OneBotClient) {
        client.onGroupMessage { event ->
            serial.computeIfAbsent(channelKey(event.groupId.toString())) {
                val channel = Channel<GroupMessageEvent>(Channel.UNLIMITED)
                scope.launch {
                    for (event in channel) {
                        launch {
                            try {
                                handleEvent(event)
                            } catch (e: Exception) {
                                log.error("Error handling event", e)
                            }
                        }
                    }
                }
                channel
            }.send(event)
        }
    }

    private suspend fun handleEvent(event: GroupMessageEvent) {
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
