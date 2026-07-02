package uesugi.core.cleanup

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import okio.Path.Companion.toPath
import uesugi.common.toolkit.logger
import uesugi.core.component.storage.ObjectStorage
import uesugi.core.message.resource.ResourceService
import uesugi.core.message.resource.ThumbnailService
import uesugi.core.state.meme.MemeRepository
import kotlin.time.Duration.Companion.days

class ResourceCleanupService(
    private val resourceService: ResourceService,
    private val storage: ObjectStorage,
    private val thumbnailService: ThumbnailService,
    private val memeRepository: MemeRepository
) {
    companion object {
        private val log = logger()
    }

    fun cleanupOldResources(retentionDays: Int, batchSize: Int = 100): Int {
        val cutoff = Clock.System.now()
            .minus(retentionDays.days)
            .toLocalDateTime(TimeZone.currentSystemDefault())

        var totalDeleted = 0
        while (true) {
            val resources = resourceService.findResourcesOlderThan(cutoff, batchSize)
            if (resources.isEmpty()) break

            resources.forEach { resource ->
                val resourceId = resource.id ?: return@forEach
                val memes = memeRepository.findMemesByResourceId(resourceId)
                if (memes.isNotEmpty()) return@forEach

                val deleted = resourceService.deleteResource(resourceId)
                if (deleted) {
                    storage.delete(resource.url.toPath())
                    totalDeleted++
                }
            }
        }
        if (totalDeleted > 0) {
            log.info("Cleaned up $totalDeleted old resources (retention=$retentionDays days)")
        }
        return totalDeleted
    }

    fun cleanupOrphanThumbnails(retentionDays: Int): Int {
        val cutoff = System.currentTimeMillis() - retentionDays.toLong() * 86_400_000L
        var deletedCount = 0
        val entries = thumbnailService.getPathMapEntries()
        for (entry in entries) {
            val originalPath = entry.key
            val thumbPath = entry.value
            if (!storage.exists(originalPath.toPath())) {
                val file = java.io.File(thumbPath)
                if (file.exists() && file.lastModified() < cutoff) {
                    thumbnailService.deleteThumbnail(originalPath)
                    deletedCount++
                }
            }
        }
        if (deletedCount > 0) {
            log.info("Cleaned up $deletedCount orphan thumbnails (retention=$retentionDays days)")
        }
        return deletedCount
    }
}
