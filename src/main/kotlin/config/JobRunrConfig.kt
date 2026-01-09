package uesugi.config

import org.jobrunr.configuration.JobRunr
import org.jobrunr.server.JobActivator
import org.jobrunr.storage.InMemoryStorageProvider
import org.koin.core.context.GlobalContext


class JobRunrConfig {
    fun start() {
        JobRunr.configure()
            .useJobActivator(Activator)
            .useStorageProvider(InMemoryStorageProvider())
            .useBackgroundJobServer()
            .useDashboard()
            .initialize()
    }

    object Activator : JobActivator {
        override fun <T : Any> activateJob(type: Class<T>): T {
            return GlobalContext.get().get(type.kotlin)
        }
    }
}