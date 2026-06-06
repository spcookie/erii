package uesugi.config

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
    fun start(): JobScheduler {
        val storageProvider = H2StorageProvider(dataSource)

        val threeDaysAgo = Instant.now().minus(1, ChronoUnit.DAYS)
        storageProvider.deleteJobsPermanently(StateName.SUCCEEDED, threeDaysAgo)
        storageProvider.deleteJobsPermanently(StateName.DELETED, threeDaysAgo)

        return JobRunr.configure()
            .useJobActivator(Activator)
            .useStorageProvider(storageProvider)
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
            .jobScheduler
    }

    object Activator : JobActivator {
        override fun <T : Any> activateJob(type: Class<T>): T {
            return type.kotlin.objectInstance ?: GlobalContext.get().get(type.kotlin)
        }
    }
}