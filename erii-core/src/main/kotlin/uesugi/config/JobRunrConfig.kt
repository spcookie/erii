package uesugi.config

import org.jobrunr.configuration.JobRunr
import org.jobrunr.scheduling.JobScheduler
import org.jobrunr.server.JobActivator
import org.jobrunr.storage.sql.h2.H2StorageProvider
import org.koin.core.context.GlobalContext
import javax.sql.DataSource


class JobRunrConfig(
    val dataSource: DataSource
) {
    fun start(): JobScheduler {
        return JobRunr.configure()
            .useJobActivator(Activator)
            .useStorageProvider(H2StorageProvider(dataSource))
            .useBackgroundJobServer()
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