package uesugi.plugin.rollpig

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import uesugi.plugin.rollpig.service.RollPigService
import uesugi.plugin.rollpig.store.RollPigStore
import uesugi.spi.Meta
import uesugi.spi.getGroup

object RollPigContextFactory {

    fun createFromMeta(
        meta: Meta,
        store: RollPigStore,
        service: RollPigService,
        scope: CoroutineScope
    ): RollPigContext {
        val userId = meta.senderId?.toLongOrNull() ?: 0L
        val senderNick = meta.senderId ?: "User"

        return RollPigContext(
            store = store,
            service = service,
            groupId = meta.groupId,
            senderId = userId,
            senderNick = senderNick,
            sendMessage = { msg ->
                scope.launch {
                    meta.getGroup().sendMessage(msg)
                }
            },
            createImage = { bytes ->
                runBlocking {
                    val imageRes = bytes.inputStream().use { it.toExternalResource() }
                    imageRes.use { res ->
                        meta.getGroup().uploadImage(res)
                    }
                }
            }
        )
    }
}