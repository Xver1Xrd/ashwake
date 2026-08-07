package dev.ashwake.domain.repository.habits

import dev.ashwake.domain.engine.habit.ScorePoint
import dev.ashwake.domain.model.habits.EntrySource
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitEntry
import dev.ashwake.domain.model.habits.HabitPause
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.habits.SkipReason
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Всё, что нужно экрану одной привычки: история, ряд score, остаток заморозок. */
data class HabitDetail(
    val progress: HabitWithProgress,
    val entries: Map<LocalDate, HabitEntry>,
    val scoreSeries: List<ScorePoint>,
    val excludedDays: Set<LocalDate>
)

interface HabitRepository {

    fun observeHabits(includeArchived: Boolean = false): Flow<List<Habit>>

    /** Карточки списка: привычка вместе с посчитанными score, стриком и отметкой дня. */
    fun observeHabitsWithProgress(today: LocalDate): Flow<List<HabitWithProgress>>

    fun observeDetail(habitId: Long, today: LocalDate, historyDays: Long = 365): Flow<HabitDetail?>

    suspend fun getHabit(id: Long): Habit?

    suspend fun upsertHabit(habit: Habit): Long

    suspend fun archiveHabit(id: Long)

    suspend fun deleteHabit(id: Long)

    /**
     * Отметка за день. Одна операция на все статусы: «сделал», «минимум»,
     * «пропустил» с причиной и счётчик — иначе четыре похожих метода
     * разъезжаются в мелочах вроде записи времени отметки.
     */
    suspend fun mark(
        habitId: Long,
        date: LocalDate,
        status: EntryStatus,
        value: Float? = null,
        note: String? = null,
        skipReasonId: Long? = null,
        source: EntrySource = EntrySource.MANUAL
    )

    /** Снять отметку целиком — повторное нажатие по уже отмеченной привычке. */
    suspend fun clearMark(habitId: Long, date: LocalDate)

    /** @return false, если месячная квота заморозок исчерпана. */
    suspend fun freeze(habitId: Long, date: LocalDate): Boolean

    suspend fun unfreeze(habitId: Long, date: LocalDate)

    fun observePauses(): Flow<List<HabitPause>>

    /** `habitId == null` — пауза всех привычек сразу (режим отпуска). */
    suspend fun pause(habitId: Long?, from: LocalDate, to: LocalDate?, reason: String? = null)

    suspend fun resumePause(pauseId: Long)

    fun observeSkipReasons(): Flow<List<SkipReason>>

    suspend fun habitsWithReminders(): List<Habit>

    /** Заводит встроенные причины пропуска, если их ещё нет. Идемпотентно. */
    suspend fun ensureBuiltinData()
}
