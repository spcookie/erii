package uesugi.toolkit

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.reflect.KClass

@Suppress("UNCHECKED_CAST")
@OptIn(DelicateCoroutinesApi::class)
object EventBus {

    private val asyncFlows = mutableMapOf<KClass<*>, MutableSharedFlow<Any>>()
    private val asyncLock = Any()

    private val syncSubscribers = mutableMapOf<KClass<*>, MutableList<(Any) -> Unit>>()
    private val syncLock = Any()

    fun <T : Any> postAsync(event: T) {
        val flow = getOrCreateAsyncFlow(event::class)
        GlobalScope.launch { flow.emit(event) }
    }

    fun <T : Any> subscribeAsync(
        eventClass: KClass<T>,
        scope: CoroutineScope,
        onEvent: suspend (T) -> Unit
    ): Job {
        val flow = getOrCreateAsyncFlow(eventClass)
        return flow
            .filter { eventClass.isInstance(it) }
            .map { it as T }
            .onEach { onEvent(it) }
            .launchIn(scope)
    }

    private fun getOrCreateAsyncFlow(kClass: KClass<*>): MutableSharedFlow<Any> {
        synchronized(asyncLock) {
            return asyncFlows.getOrPut(kClass) { MutableSharedFlow(extraBufferCapacity = 64) }
        }
    }

    fun <T : Any> postSync(event: T) {
        val subscribers = getSyncSubscribers(event::class)
        subscribers.forEach { it(event) }
    }

    fun <T : Any> subscribeSync(eventClass: KClass<T>, onEvent: (T) -> Unit) {
        val list = getSyncSubscribers(eventClass)
        list.add(onEvent as (Any) -> Unit)
    }

    private fun getSyncSubscribers(kClass: KClass<*>): MutableList<(Any) -> Unit> {
        synchronized(syncLock) {
            return syncSubscribers.getOrPut(kClass) { mutableListOf() }
        }
    }

    fun <T : Any> unsubscribeSync(eventClass: KClass<T>, onEvent: (T) -> Unit) {
        val list = getSyncSubscribers(eventClass)
        list.remove(onEvent as (Any) -> Unit)
    }
}