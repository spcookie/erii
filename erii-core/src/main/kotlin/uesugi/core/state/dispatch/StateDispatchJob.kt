package uesugi.core.state.dispatch

import org.jobrunr.scheduling.JobScheduler
import uesugi.common.toolkit.ConfigHolder
import uesugi.common.toolkit.logger

class StateDispatchJob(
    private val jobScheduler: JobScheduler,
    private val coordinator: StateWorkCoordinator
) {
    private val log = logger()

    private val legacyRecurringJobIds = listOf(
        "emotion-job",
        "flow-job",
        "volition-job",
        "memory-job",
        "summary-job",
        "evolution-job",
        "meme-collect-job",
        "meme-extract-job"
    )

    fun open() {
        val profile = ConfigHolder.getStateTuning().dispatch.profile
        val minutes = profile.reconciliationInterval.inWholeMinutes.coerceAtLeast(1)
        val cron = if (minutes == 1L) "* * * * *" else "*/$minutes * * * *"

        removeLegacyRecurringJobs()
        coordinator.start()
        coordinator.reconcile()
        jobScheduler.scheduleRecurrently(
            "state-reconciliation-job",
            cron,
            ::reconcile
        )
        log.info("State dispatch started, profile=$profile, reconciliation=${minutes}m")
    }

    fun reconcile() {
        coordinator.reconcile()
    }

    private fun removeLegacyRecurringJobs() {
        legacyRecurringJobIds.forEach { id ->
            runCatching { jobScheduler.deleteRecurringJob(id) }
                .onFailure { error -> log.warn("Failed to remove legacy state job $id", error) }
        }
    }
}
