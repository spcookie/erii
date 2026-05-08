package uesugi.plugin.rollpig

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import uesugi.plugin.rollpig.service.RollPigService
import uesugi.plugin.rollpig.store.RollPigStore
import uesugi.spi.Meta

class RollPigCommandHandler(
    private val store: RollPigStore,
    private val service: RollPigService,
    private val scope: CoroutineScope
) {

    private val log = KotlinLogging.logger {}

    fun handle(meta: Meta) = RollPigContextFactory.createFromMeta(
        meta = meta,
        store = store,
        service = service,
        scope = scope
    )

    fun handleWithError(meta: Meta, parser: (RollPigContext) -> Unit) {
        try {
            val ctx = handle(meta)
            parser(ctx)
        } catch (e: Exception) {
            log.error(e) { "Error handling command" }
        }
    }
}