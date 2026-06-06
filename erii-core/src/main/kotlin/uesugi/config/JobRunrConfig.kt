package uesugi.config

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jobrunr.configuration.JobRunr
import org.jobrunr.jobs.states.StateName
import org.jobrunr.scheduling.JobScheduler
import org.jobrunr.server.BackgroundJobServerConfiguration
import org.jobrunr.server.JobActivator
import org.jobrunr.storage.sql.h2.H2StorageProvider
import org.koin.core.context.GlobalContext
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.sql.DataSource


class JobRunrConfig(
    val dataSource: DataSource
) {

    private val log = KotlinLogging.logger {}

    fun start(): JobScheduler {
        val jobRunr = JobRunr.configure()
            .useJobActivator(Activator)
            .useStorageProvider(H2StorageProvider(dataSource))
            .useBackgroundJobServer(
                BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration()
                    .andPollIntervalInSeconds(30)
                    .andDeleteSucceededJobsAfter(Duration.ofHours(1))
                    .andPermanentlyDeleteDeletedJobsAfter(Duration.ofHours(1))
            )
            .useDashboardIf(
                System.getProperty("jobrunr.dashboard.enabled", "false").toBoolean(),
                System.getProperty("jobrunr.dashboard.port", "8000").toInt()
            )
            .initialize()

        val storageProvider = JobRunr.getBackgroundJobServer().storageProvider
        val oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS)
        val deletedSucceeded = storageProvider.deleteJobsPermanently(StateName.SUCCEEDED, oneDayAgo)
        val deletedDeleted = storageProvider.deleteJobsPermanently(StateName.DELETED, oneDayAgo)
        if (deletedSucceeded > 0 || deletedDeleted > 0) {
            log.info { "[JobRunr] Cleaned up old jobs: $deletedSucceeded succeeded, $deletedDeleted deleted (before $oneDayAgo)" }
        }

        return jobRunr.jobScheduler
    }

    object Activator : JobActivator {
        override fun <T : Any> activateJob(type: Class<T>): T {
            return type.kotlin.objectInstance ?: GlobalContext.get().get(type.kotlin)
        }
    }
}