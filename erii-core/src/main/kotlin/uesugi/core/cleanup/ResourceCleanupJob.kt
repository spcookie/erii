package uesugi.core.cleanup

import org.jobrunr.scheduling.BackgroundJob
import uesugi.common.toolkit.ConfigHolder
import uesugi.common.toolkit.logger

class ResourceCleanupJob(
    private val cleanupService: ResourceCleanupService
) {
    companion object {
        private val log = logger()
    }

    fun openTimingTriggerSignal() {
        BackgroundJob.scheduleRecurrently(
            "resource-cleanup-job",
            "0 4 * * *",
            ::doCleanup
        )
        log.info("Resource cleanup job scheduled, daily at 04:00")
    }

    fun doCleanup() {
        val config = ConfigHolder.getResourceCleanup()
        val count = cleanupService.cleanupOldResources(config.retentionDays)
        val orphanCount = cleanupService.cleanupOrphanThumbnails(config.thumbnailRetentionDays)
        log.info("Resource cleanup completed: $count resources, $orphanCount orphan thumbnails")
    }
}
