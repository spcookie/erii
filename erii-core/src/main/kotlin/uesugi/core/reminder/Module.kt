package uesugi.core.reminder

import org.koin.dsl.module

val reminderModule = module {
    single { ReminderService(get()) }
}