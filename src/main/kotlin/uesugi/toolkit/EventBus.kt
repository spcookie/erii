package uesugi.toolkit

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST", "UNUSED")
object EventBus {

    /* ================= async ================= */

    @PublishedApi
    internal object AsyncBus {

        // sendReplay = 0 确保新订阅者不会收到旧消息
        private val bus = MutableSharedFlow<Any>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )

        fun post(event: Any) {
            // 如果必须保证发送成功不丢失，可以使用 emit，但这里作为 EventBus，防止阻塞发送端通常更重要
            bus.tryEmit(event)
        }

        fun <T : Any> subscribe(
            kClass: KClass<T>,
            scope: CoroutineScope,
            once: Boolean = false,
            onEvent: suspend (T) -> Unit
        ): Job {
            val flow = bus
                .filter { kClass.isInstance(it) } // 先过滤类型
                .map { it as T }                  // 强转

            val targetFlow = if (once) flow.take(1) else flow

            return targetFlow
                .onEach {
                    try {
                        onEvent(it)
                    } catch (e: Exception) {
                        e.printStackTrace() // 防止消费者崩溃导致流终止
                    }
                }
                .launchIn(scope)
        }
    }

    /* ================= sync ================= */

    @PublishedApi
    internal object SyncBus {

        private data class Subscriber(
            val kClass: KClass<*>,
            val once: Boolean,
            val callback: (Any) -> Unit,
            val originalRef: Any // 修复核心 Bug：保存原始 lambda 引用用于对比
        )

        // CopyOnWriteArrayList 允许在遍历时无需加锁，性能更好且避免 ConcurrentModificationException
        private val subscribers = CopyOnWriteArrayList<Subscriber>()

        fun post(event: Any) {
            // CopyOnWriteArrayList 迭代是线程安全的，不需要 snapshot 副本
            subscribers.forEach { sub ->
                if (sub.kClass.isInstance(event)) {
                    sub.callback(event)
                    if (sub.once) {
                        subscribers.remove(sub)
                    }
                }
            }
        }

        fun <T : Any> subscribe(
            kClass: KClass<T>,
            once: Boolean = false,
            onEvent: (T) -> Unit
        ) {
            val subscriber = Subscriber(
                kClass = kClass,
                once = once,
                callback = { event ->
                    // 这里的 try-catch 防止回调报错中断整个 post 循环
                    try {
                        onEvent(event as T)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                originalRef = onEvent // 保存原始引用
            )
            subscribers.add(subscriber)
        }

        fun <T : Any> unsubscribe(
            kClass: KClass<T>,
            onEvent: (T) -> Unit
        ) {
            // 修复：使用 originalRef 进行引用对比
            subscribers.removeIf {
                it.kClass == kClass && it.originalRef === onEvent
            }
        }
    }

    /* ================= public api ================= */

    fun postAsync(event: Any) = AsyncBus.post(event)

    inline fun <reified T : Any> subscribeAsync(
        scope: CoroutineScope,
        noinline onEvent: suspend (T) -> Unit
    ): Job =
        AsyncBus.subscribe(T::class, scope, once = false, onEvent)

    inline fun <reified T : Any> subscribeOnceAsync(
        scope: CoroutineScope,
        noinline onEvent: suspend (T) -> Unit
    ): Job =
        AsyncBus.subscribe(T::class, scope, once = true, onEvent)

    fun unsubscribeAsync(job: Job) = job.cancel()

    fun postSync(event: Any) = SyncBus.post(event)

    inline fun <reified T : Any> subscribeSync(
        noinline onEvent: (T) -> Unit
    ) =
        SyncBus.subscribe(T::class, once = false, onEvent)

    inline fun <reified T : Any> subscribeOnceSync(
        noinline onEvent: (T) -> Unit
    ) =
        SyncBus.subscribe(T::class, once = true, onEvent)

    inline fun <reified T : Any> unsubscribeSync(
        noinline onEvent: (T) -> Unit
    ) =
        SyncBus.unsubscribe(T::class, onEvent)
}