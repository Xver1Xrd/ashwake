package dev.ashwake.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.ashwake.core.time.AppClock
import dev.ashwake.core.time.SystemAppClock
import dev.ashwake.data.repository.tasks.ProjectRepositoryImpl
import dev.ashwake.data.repository.tasks.TagRepositoryImpl
import dev.ashwake.data.repository.tasks.TaskRepositoryImpl
import dev.ashwake.domain.repository.tasks.ProjectRepository
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
}
