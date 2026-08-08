package dev.ashwake.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ashwake.domain.engine.reward.RewardConfig
import dev.ashwake.domain.scheduler.HabitReminderScheduler
import dev.ashwake.domain.scheduler.TaskReminderScheduler
import dev.ashwake.platform.alarm.AlarmHabitReminderScheduler
import dev.ashwake.platform.alarm.AlarmTaskReminderScheduler
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {

    @Binds @Singleton
    abstract fun bindTaskReminderScheduler(
        impl: AlarmTaskReminderScheduler
    ): TaskReminderScheduler

    @Binds @Singleton
    abstract fun bindHabitReminderScheduler(
        impl: AlarmHabitReminderScheduler
    ): HabitReminderScheduler

    companion object {
        /** Баланс экономики. Правится в одном файле — RewardConfig (п. 14). */
        @Provides
        @Singleton
        fun provideRewardConfig(): RewardConfig = RewardConfig.DEFAULT
    }
}
