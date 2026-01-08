package uesugi.toolkit

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
@OptIn(DelicateCoroutinesApi::class)
object EventBus {

    /* ================= async ================= */

    @PublishedApi
    internal object AsyncBus {

        private val flows = mutableMapOf<KClass<*>, MutableSharedFlow<Any>>()
        private val lock = Any()

        private fun flowOf(kClass: KClass<*>): MutableSharedFlow<Any> =
            synchronized(lock) {
                flows.getOrPut(kClass) {
                    MutableSharedFlow(extraBufferCapacity = 64)
                }
            }

        fun post(event: Any) {
            GlobalScope.launch {
                flowOf(event::class).emit(event)
            }
        }

        fun <T : Any> subscribe(
            kClass: KClass<T>,
            scope: CoroutineScope,
            once: Boolean = false,
            onEvent: suspend (T) -> Unit
        ): Job {
            var f: Flow<T> = flowOf(kClass)
                .filter { kClass.isInstance(it) }
                .map { it as T }

            if (once) {
                f = f.take(1)
            }

            return f.onEach(onEvent).launchIn(scope)
        }
    }

    /* ================= sync ================= */

    @PublishedApi
    internal object SyncBus {

        private val subscribers = mutableMapOf<KClass<*>, MutableList<(Any) -> Unit>>()
        private val lock = Any()

        fun post(event: Any) {
            val snapshot = synchronized(lock) {
                subscribers[event::class]?.toList().orEmpty()
            }
            snapshot.forEach { it(event) }
        }

        fun <T : Any> subscribe(
            kClass: KClass<T>,
            once: Boolean = false,
            onEvent: (T) -> Unit
        ) {
            val list = synchronized(lock) {
                subscribers.getOrPut(kClass) { mutableListOf() }
            }

            if (!once) {
                list.add(onEvent as (Any) -> Unit)
                return
            }

            lateinit var wrapper: (Any) -> Unit
            wrapper = { event ->
                onEvent(event as T)
                unsubscribe(kClass, wrapper as (T) -> Unit)
            }

            list.add(wrapper)
        }

        fun <T : Any> unsubscribe(
            kClass: KClass<T>,
            onEvent: (T) -> Unit
        ) {
            synchronized(lock) {
                subscribers[kClass]?.remove(onEvent as (Any) -> Unit)
            }
        }
    }

    fun postAsync(event: Any) = AsyncBus.post(event)

    inline fun <reified T : Any> subscribeAsync(
        scope: CoroutineScope,
        noinline onEvent: suspend (T) -> Unit
    ): Job = AsyncBus.subscribe(T::class, scope, once = false, onEvent)

    inline fun <reified T : Any> subscribeOnceAsync(
        scope: CoroutineScope,
        noinline onEvent: suspend (T) -> Unit
    ): Job = AsyncBus.subscribe(T::class, scope, once = true, onEvent)

    fun unsubscribeAsync(job: Job) = job.cancel()

    fun postSync(event: Any) = SyncBus.post(event)

    inline fun <reified T : Any> subscribeSync(
        noinline onEvent: (T) -> Unit
    ) = SyncBus.subscribe(T::class, once = false, onEvent)

    inline fun <reified T : Any> subscribeOnceSync(
        noinline onEvent: (T) -> Unit
    ) = SyncBus.subscribe(T::class, once = true, onEvent)

    inline fun <reified T : Any> unsubscribeSync(
        noinline onEvent: (T) -> Unit
    ) = SyncBus.unsubscribe(T::class, onEvent)

}
