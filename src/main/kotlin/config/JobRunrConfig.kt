package uesugi.config

import org.jobrunr.configuration.JobRunr
import org.jobrunr.storage.InMemoryStorageProvider


class JobRunrConfig {
    init {
        JobRunr.configure()
            .useStorageProvider(InMemoryStorageProvider())
            .useBackgroundJobServer()
//            .useDashboard()
            .initialize()
    }
}