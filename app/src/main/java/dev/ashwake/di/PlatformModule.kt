package dev.ashwake.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ashwake.domain.scheduler.TaskReminderScheduler
import dev.ashwake.platform.alarm.AlarmTaskReminderScheduler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {

    @Binds @Singleton
    abstract fun bindTaskReminderScheduler(
        impl: AlarmTaskReminderScheduler
    ): TaskReminderScheduler
}
