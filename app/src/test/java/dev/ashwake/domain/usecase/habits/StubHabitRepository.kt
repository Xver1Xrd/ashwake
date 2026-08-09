package dev.ashwake.domain.usecase.habits

import dev.ashwake.domain.model.habits.EntrySource
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitAnchor
import dev.ashwake.domain.model.habits.HabitPause
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.habits.SkipReason
import dev.ashwake.domain.repository.habits.HabitDetail
import dev.ashwake.domain.repository.habits.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate

/**
 * Пустая реализация репозитория для юнит-тестов.
 *
 * Тест переопределяет ровно те методы, которые проверяет, и не тащит за
 * собой ни Room, ни расчёт score. Методы, которых тест не касается, здесь
 * не бросают исключение намеренно: падение в неожиданном месте помешало бы
 * увидеть настоящую причину.
 */
abstract class StubHabitRepository : HabitRepository {

    override fun observeHabits(includeArchived: Boolean): Flow<List<Habit>> = flowOf(emptyList())

    override fun observeHabitsWithProgress(today: LocalDate): Flow<List<HabitWithProgress>> =
        flowOf(emptyList())

    override fun observeDetail(
        habitId: Long,
        today: LocalDate,
        historyDays: Long
    ): Flow<HabitDetail?> = flowOf(null)

    override suspend fun getHabit(id: Long): Habit? = null

    override suspend fun upsertHabit(habit: Habit): Long = habit.id

    override suspend fun archiveHabit(id: Long) = Unit

    override suspend fun deleteHabit(id: Long) = Unit

    override suspend fun mark(
        habitId: Long,
        date: LocalDate,
        status: EntryStatus,
        value: Float?,
        note: String?,
        skipReasonId: Long?,
        source: EntrySource
    ) = Unit

    override suspend fun clearMark(habitId: Long, date: LocalDate) = Unit

    override suspend fun progressFor(habitId: Long, date: LocalDate): HabitWithProgress? = null

    override suspend fun anchorsTriggeredByHabit(
        habitId: Long,
        today: LocalDate
    ): List<HabitAnchor> = emptyList()

    override suspend fun anchorsTriggeredByRoutine(
        routineId: Long,
        today: LocalDate
    ): List<HabitAnchor> = emptyList()

    override suspend fun anchorsTriggeredByTag(
        tagId: Long,
        today: LocalDate
    ): List<HabitAnchor> = emptyList()

    override suspend fun markAnchorFired(anchorId: Long, date: LocalDate) = Unit

    override suspend fun freeze(habitId: Long, date: LocalDate): Boolean = false

    override suspend fun unfreeze(habitId: Long, date: LocalDate) = Unit

    override fun observePauses(): Flow<List<HabitPause>> = flowOf(emptyList())

    override suspend fun pause(
        habitId: Long?,
        from: LocalDate,
        to: LocalDate?,
        reason: String?
    ) = Unit

    override suspend fun resumePause(pauseId: Long) = Unit

    override fun observeSkipReasons(): Flow<List<SkipReason>> = flowOf(emptyList())

    override suspend fun habitsWithReminders(): List<Habit> = emptyList()

    override suspend fun ensureBuiltinData() = Unit
}
