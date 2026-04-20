package uesugi.core.reminder

import org.koin.dsl.module
import org.koin.dsl.onClose

val reminderModule = module {
    single { ReminderService(get()) } onClose { it?.stop() }
}