package uesugi.core.plugin

import org.jobrunr.scheduling.JobScheduler
import uesugi.spi.Scheduler
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.toJavaDuration

class SchedulerImpl(
    private val pluginName: String,
    private val jobScheduler: JobScheduler
) : Scheduler {

    // Recurring jobs: 使用 String ID，通过 deleteRecurringJob 删除
    private val recurringJobIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    // One-time jobs: 映射用户 ID -> JobRunr UUID，通过 delete(UUID) 删除
    private val oneTimeJobIds = ConcurrentHashMap<String, UUID>()

    private fun qualifiedId(id: String) = "$pluginName.$id"

    override fun scheduleRecurrently(id: String, cron: String, action: () -> Unit) {
        val qid = qualifiedId(id)
        SchedulerBridge.register(qid, action)
        recurringJobIds += qid
        jobScheduler.scheduleRecurrently(qid, cron) {
            SchedulerBridge.execute(qid)
        }
    }

    override fun scheduleRecurrently(id: String, interval: Duration, action: () -> Unit) {
        val qid = qualifiedId(id)
        SchedulerBridge.register(qid, action)
        recurringJobIds += qid
        jobScheduler.scheduleRecurrently(qid, interval.toJavaDuration()) {
            SchedulerBridge.execute(qid)
        }
    }

    override fun schedule(id: String, delay: Duration, action: () -> Unit) {
        val qid = qualifiedId(id)
        SchedulerBridge.register(qid, action)
        // schedule 不接受自定义 String ID，返回 JobId
        val jobId = jobScheduler.schedule(java.time.Instant.now().plus(delay.toJavaDuration())) {
            SchedulerBridge.execute(qid)
        }
        oneTimeJobIds[qid] = jobId.asUUID()
    }

    override fun enqueue(id: String, action: () -> Unit) {
        val qid = qualifiedId(id)
        SchedulerBridge.register(qid, action)
        val jobId = jobScheduler.enqueue { SchedulerBridge.execute(qid) }
        oneTimeJobIds[qid] = jobId.asUUID()
    }

    override fun cancel(id: String) {
        val qid = qualifiedId(id)
        if (qid in recurringJobIds) {
            jobScheduler.deleteRecurringJob(qid)
            recurringJobIds -= qid
        } else {
            oneTimeJobIds.remove(qid)?.let { uuid ->
                jobScheduler.delete(uuid)
            }
        }
        SchedulerBridge.unregister(qid)
    }

    override fun close() {
        // 先取快照再遍历，避免并发修改
        val recurringIds = recurringJobIds.toList()
        recurringIds.forEach { qid ->
            runCatching { jobScheduler.deleteRecurringJob(qid) }
            SchedulerBridge.unregister(qid)
        }
        recurringJobIds.clear()

        val oneTimeIds = oneTimeJobIds.toMap()
        oneTimeIds.forEach { (qid, uuid) ->
            runCatching { jobScheduler.delete(uuid) }
            SchedulerBridge.unregister(qid)
        }
        oneTimeJobIds.clear()
    }
}
