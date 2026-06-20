package uesugi.plugin.builtin

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

object CommandQueue {

    private val log = KotlinLogging.logger {}

    // -- serial (FIFO + timeout) --

    private class Job<T>(val block: suspend () -> T, val deferred: CompletableDeferred<T?>)

    private val channels = ConcurrentHashMap<String, Channel<Job<*>>>()

    /**
     * 按 key 串行排队执行，FIFO。超时未排到则跳过，返回 null。
     */
    suspend fun <T> serial(key: String, timeout: Duration, block: suspend () -> T): T? {
        val channel = channels.computeIfAbsent(key) {
            Channel<Job<*>>(Channel.UNLIMITED).also { ch ->
                CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                    for (job in ch) {
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val j = job as Job<Any?>
                            val result = j.block()
                            j.deferred.complete(result)
                        } catch (e: Exception) {
                            log.warn(e) { "Command execution failed for key=$key" }
                            job.deferred.complete(null)
                        }
                    }
                }
            }
        }

        val deferred = CompletableDeferred<T?>()
        channel.send(Job(block, deferred))

        return withTimeoutOrNull(timeout) {
            deferred.await()
        }.also { result ->
            if (result == null) {
                log.debug { "Command timed out for key=$key after ${timeout.inWholeSeconds}s" }
            }
        }
    }

    // -- serialDedup (tryLock + dedup) --

    private val mutexes = ConcurrentHashMap<String, Mutex>()

    private fun mutexFor(key: String): Mutex =
        mutexes.computeIfAbsent(key) { Mutex() }

    /**
     * 按 key 串行 + 去重。如果同一 key 已有任务在执行，新请求直接跳过返回 null。
     */
    suspend fun <T> serialDedup(key: String, block: suspend () -> T): T? {
        val mutex = mutexFor(key)
        if (!mutex.tryLock()) {
            log.debug { "Command skipped for key=$key (already running)" }
            return null
        }
        try {
            return block()
        } finally {
            mutex.unlock()
        }
    }
}
