package uesugi.plugin.animal

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import uesugi.plugin.animal.service.AnimalService
import uesugi.plugin.animal.store.AnimalStore
import uesugi.spi.Meta
import uesugi.spi.getGroup

class AnimalCommandHandler(
    private val store: AnimalStore,
    private val service: AnimalService,
    private val serverPort: Int,
    private val serverBasePath: String,
    private val scope: CoroutineScope
) {

    private val log = KotlinLogging.logger {}

    fun handle(meta: Meta): AnimalContext {
        val ctx = AnimalContextFactory.createFromMeta(
            meta = meta,
            store = store,
            service = service,
            serverPort = serverPort,
            serverBasePath = serverBasePath
        )

        return ctx.copy(
            sendMessage = { msg ->
                scope.launch {
                    meta.getGroup().sendMessage(msg)
                }
            }
        )
    }

    fun handleWithError(meta: Meta, parser: (AnimalContext) -> Unit) {
        try {
            val ctx = handle(meta)
            parser(ctx)
        } catch (e: Exception) {
            log.error(e) { "Error handling command" }
        }
    }
}
