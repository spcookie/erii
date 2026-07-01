package uesugi.core.cleanup

import org.koin.dsl.module

val cleanupModule = module {
    single { ResourceCleanupService(get(), get(), get(), get()) }
    single { ResourceCleanupJob(get()) }
}
