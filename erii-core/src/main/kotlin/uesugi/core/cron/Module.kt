package uesugi.core.cron

import org.koin.dsl.module
import org.koin.dsl.onClose

val cronModule = module {
    single { CronService(get()) } onClose { it?.stop() }
}
