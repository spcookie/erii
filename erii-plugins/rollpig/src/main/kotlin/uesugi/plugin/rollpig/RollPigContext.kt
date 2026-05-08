package uesugi.plugin.rollpig

import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.buildMessageChain
import uesugi.plugin.rollpig.service.RollPigService
import uesugi.plugin.rollpig.store.RollPigStore

data class RollPigContext(
    val store: RollPigStore,
    val service: RollPigService,
    val groupId: String,
    val senderId: Long,
    val senderNick: String,
    val isAdmin: Boolean,
    val sendMessage: (MessageChain) -> Unit,
    val createImage: (ByteArray) -> Image?,
) {
    fun sendText(text: String) {
        sendMessage(buildMessageChain {
            +At(senderId)
            +" "
            +text
        })
    }

    fun sendImage(bytes: ByteArray) {
        createImage(bytes)?.let { img ->
            sendMessage(buildMessageChain { +img })
        }
    }
}