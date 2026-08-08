package dev.ashwake.platform.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.assets.CatalogLoader
import dev.ashwake.domain.repository.abstinence.AbstinenceRepository
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.domain.usecase.habits.MarkHabitUseCase
import dev.ashwake.domain.usecase.tasks.CompleteTaskUseCase

/**
 * Доступ к репозиториям из виджетов.
 *
 * `GlanceAppWidget` создаётся системой, а не Hilt, поэтому обычная инъекция
 * в него невозможна — зависимости достаются через точку входа.
 * Виджеты дёргают те же use case'ы, что и экраны: отметка из виджета
 * обязана начислять монеты и снимать будильник так же, как отметка из списка.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun taskRepository(): TaskRepository
    fun habitRepository(): HabitRepository
    fun abstinenceRepository(): AbstinenceRepository
    fun characterRepository(): CharacterRepository
    fun catalogLoader(): CatalogLoader
    fun completeTask(): CompleteTaskUseCase
    fun markHabit(): MarkHabitUseCase
    fun clock(): AppClock
}

internal fun Context.widgetDependencies(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
