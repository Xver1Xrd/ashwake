package dev.ashwake.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ashwake.core.time.AppClock
import dev.ashwake.core.time.SystemAppClock
import dev.ashwake.data.repository.abstinence.AbstinenceRepositoryImpl
import dev.ashwake.data.repository.character.CharacterRepositoryImpl
import dev.ashwake.data.repository.habits.HabitRepositoryImpl
import dev.ashwake.data.repository.routines.FocusRepositoryImpl
import dev.ashwake.data.repository.routines.RoutineRepositoryImpl
import dev.ashwake.data.repository.ritual.RitualRepositoryImpl
import dev.ashwake.data.repository.tasks.ProjectRepositoryImpl
import dev.ashwake.data.repository.timebox.TimeboxRepositoryImpl
import dev.ashwake.data.repository.tasks.TagRepositoryImpl
import dev.ashwake.data.repository.tasks.TaskRepositoryImpl
import dev.ashwake.domain.repository.abstinence.AbstinenceRepository
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.repository.routines.FocusRepository
import dev.ashwake.domain.repository.routines.RoutineRepository
import dev.ashwake.domain.repository.ritual.RitualRepository
import dev.ashwake.domain.repository.tasks.ProjectRepository
import dev.ashwake.domain.repository.timebox.TimeboxRepository
import dev.ashwake.domain.repository.tasks.TagRepository
import dev.ashwake.domain.repository.tasks.TaskRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindClock(impl: SystemAppClock): AppClock

    @Binds @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds @Singleton
    abstract fun bindProjectRepository(impl: ProjectRepositoryImpl): ProjectRepository

    @Binds @Singleton
    abstract fun bindTagRepository(impl: TagRepositoryImpl): TagRepository

    @Binds @Singleton
    abstract fun bindHabitRepository(impl: HabitRepositoryImpl): HabitRepository

    @Binds @Singleton
    abstract fun bindAbstinenceRepository(
        impl: AbstinenceRepositoryImpl
    ): AbstinenceRepository

    @Binds @Singleton
    abstract fun bindCharacterRepository(
        impl: CharacterRepositoryImpl
    ): CharacterRepository

    @Binds @Singleton
    abstract fun bindRoutineRepository(impl: RoutineRepositoryImpl): RoutineRepository

    @Binds @Singleton
    abstract fun bindFocusRepository(impl: FocusRepositoryImpl): FocusRepository

    @Binds @Singleton
    abstract fun bindTimeboxRepository(impl: TimeboxRepositoryImpl): TimeboxRepository

    @Binds @Singleton
    abstract fun bindRitualRepository(impl: RitualRepositoryImpl): RitualRepository
}
