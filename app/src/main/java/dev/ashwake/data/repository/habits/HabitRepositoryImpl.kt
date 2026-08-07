package dev.ashwake.data.repository.habits

import androidx.room.withTransaction
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.habits.HabitDao
import dev.ashwake.data.db.entity.habits.HabitFreezeEntity
import dev.ashwake.data.db.entity.habits.HabitSkipReasonEntity
import dev.ashwake.data.db.mapper.habits.toDomain
import dev.ashwake.data.db.mapper.habits.toEntity
import dev.ashwake.data.db.mapper.tasks.toEpochDayInt
import dev.ashwake.domain.engine.habit.HabitScheduleResolver
import dev.ashwake.domain.engine.habit.HabitScoreCalculator
import dev.ashwake.domain.engine.habit.StreakCalculator
import dev.ashwake.domain.model.habits.EntrySource
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitEntry
import dev.ashwake.domain.model.habits.HabitPause
import dev.ashwake.domain.model.habits.HabitWithProgress
import dev.ashwake.domain.model.habits.SkipReason
import dev.ashwake.domain.repository.habits.HabitDetail
import dev.ashwake.domain.repository.habits.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Глубина истории, на которой считаются score и стрик. */
private const val SCORE_WINDOW_DAYS = 400L

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class HabitRepositoryImpl @Inject constructor(
    private val db: AshwakeDatabase,
    private val dao: HabitDao,
    private val clock: AppClock,
    private val scoreCalculator: HabitScoreCalculator,
    private val streakCalculator: StreakCalculator,
    private val scheduleResolver: HabitScheduleResolver
) : HabitRepository {

    override fun observeHabits(includeArchived: Boolean): Flow<List<Habit>> =
        dao.observeHabits(if (includeArchived) 1 else 0)
            .map { list -> list.map { it.toDomain() } }

    /**
     * Один поток на весь экран: привычки, отметки за окно, заморозки, паузы и якоря
     * читаются четырьмя подписками, а score и стрик считаются на месте.
     *
     * Держать это в репозитории, а не в ViewModel, важно потому, что тем же
     * прогрессом пользуются уведомления и виджеты — им ViewModel недоступна.
     */
    override fun observeHabitsWithProgress(today: LocalDate): Flow<List<HabitWithProgress>> {
        val from = today.minusDays(SCORE_WINDOW_DAYS)
        return combine(
            dao.observeHabits(0),
            dao.observeEntriesInRange(from.toEpochDayInt(), today.toEpochDayInt()),
            dao.observeFreezes(),
            dao.observePauses(),
            dao.observeAnchors()
        ) { habits, entries, freezes, pauses, anchors ->
            val entriesByHabit = entries.map { it.toDomain() }.groupBy { it.habitId }
            val freezesByHabit = freezes.map { it.toDomain() }.groupBy { it.habitId }
            val pauseList = pauses.map { it.toDomain() }
            val anchorsByHabit = anchors.map { it.toDomain() }.groupBy { it.habitId }
            val monthKey = monthKeyOf(today)

            habits.map { entity ->
                val habit = entity.toDomain(anchorsByHabit[entity.id].orEmpty())
                val byDate = entriesByHabit[habit.id].orEmpty().associateBy { it.date }
                val excluded = excludedDays(habit.id, freezesByHabit, pauseList, from, today)

                val score = scoreCalculator.currentScore(
                    habit = habit, entries = byDate, excludedDays = excluded,
                    from = from, to = today
                )
                val streak = streakCalculator.compute(
                    habit = habit, entries = byDate, excludedDays = excluded,
                    from = from, today = today
                )
                val spentFreezes = freezesByHabit[habit.id].orEmpty()
                    .count { monthKeyOf(it.date) == monthKey }

                HabitWithProgress(
                    habit = habit,
                    score = score,
                    currentStreak = streak.current,
                    recordStreak = streak.record,
                    todayEntry = byDate[today],
                    dueToday = scheduleResolver.isDue(habit, today),
                    freezesLeftThisMonth =
                        (habit.freezeQuotaPerMonth - spentFreezes).coerceAtLeast(0),
                    paused = pauseList.any {
                        (it.habitId == null || it.habitId == habit.id) && it.covers(today)
                    }
                )
            }
        }
    }

    override fun observeDetail(
        habitId: Long,
        today: LocalDate,
        historyDays: Long
    ): Flow<HabitDetail?> {
        val from = today.minusDays(historyDays)
        return dao.observeHabit(habitId).flatMapLatest { entity ->
            if (entity == null) return@flatMapLatest kotlinx.coroutines.flow.flowOf(null)
            combine(
                dao.observeEntriesOf(habitId, from.toEpochDayInt(), today.toEpochDayInt()),
                dao.observeFreezes(),
                dao.observePauses(),
                dao.observeAnchors()
            ) { entries, freezes, pauses, anchors ->
                val habit = entity.toDomain(
                    anchors.map { it.toDomain() }.filter { it.habitId == habitId }
                )
                val byDate = entries.map { it.toDomain() }.associateBy { it.date }
                val excluded = excludedDays(
                    habitId,
                    freezes.map { it.toDomain() }.groupBy { it.habitId },
                    pauses.map { it.toDomain() },
                    from, today
                )
                val series = scoreCalculator.computeSeries(
                    habit = habit, entries = byDate, excludedDays = excluded,
                    from = from, to = today
                )
                val streak = streakCalculator.compute(
                    habit = habit, entries = byDate, excludedDays = excluded,
                    from = from, today = today
                )
                val monthKey = monthKeyOf(today)
                val spentFreezes = freezes.map { it.toDomain() }
                    .count { it.habitId == habitId && monthKeyOf(it.date) == monthKey }

                HabitDetail(
                    progress = HabitWithProgress(
                        habit = habit,
                        score = series.lastOrNull()?.score ?: 0f,
                        currentStreak = streak.current,
                        recordStreak = streak.record,
                        todayEntry = byDate[today],
                        dueToday = scheduleResolver.isDue(habit, today),
                        freezesLeftThisMonth =
                            (habit.freezeQuotaPerMonth - spentFreezes).coerceAtLeast(0),
                        paused = pauses.map { it.toDomain() }.any {
                            (it.habitId == null || it.habitId == habitId) && it.covers(today)
                        }
                    ),
                    entries = byDate,
                    scoreSeries = series,
                    excludedDays = excluded
                )
            }
        }
    }

    override suspend fun getHabit(id: Long): Habit? = dao.getHabit(id)?.toDomain()

    override suspend fun upsertHabit(habit: Habit): Long = db.withTransaction {
        val prepared =
            if (habit.createdAt.toEpochMilli() == 0L) habit.copy(createdAt = clock.now())
            else habit
        val positioned =
            if (habit.id == 0L) prepared.copy(position = dao.maxPosition() + 1) else prepared

        val id = dao.upsert(positioned.toEntity()).let { if (it == -1L) habit.id else it }
        dao.clearAnchors(id)
        if (habit.anchors.isNotEmpty()) {
            dao.insertAnchors(habit.anchors.map { it.copy(habitId = id).toEntity() })
        }
        id
    }

    override suspend fun archiveHabit(id: Long) = dao.archive(id)

    override suspend fun deleteHabit(id: Long) = dao.deleteById(id)

    override suspend fun mark(
        habitId: Long,
        date: LocalDate,
        status: EntryStatus,
        value: Float?,
        note: String?,
        skipReasonId: Long?,
        source: EntrySource
    ) {
        db.withTransaction {
            val habit = dao.getHabit(habitId)?.toDomain() ?: return@withTransaction
            val existing = dao.entryAt(habitId, date.toEpochDayInt())
            val resolvedValue = value ?: when (status) {
                EntryStatus.DONE -> habit.targetValue
                EntryStatus.MINIMUM -> habit.minimumValue ?: habit.targetValue
                else -> 0f
            }
            val entry = HabitEntry(
                id = existing?.id ?: 0,
                habitId = habitId,
                date = date,
                status = status,
                value = resolvedValue,
                // Заметка не стирается при повторной отметке, если новую не передали
                note = note ?: existing?.note,
                completedAt = clock.now(),
                skipReasonId = skipReasonId ?: existing?.skipReasonId,
                source = source
            )
            dao.upsertEntry(entry.toEntity())
        }
    }

    override suspend fun clearMark(habitId: Long, date: LocalDate) {
        dao.deleteEntry(habitId, date.toEpochDayInt())
    }

    override suspend fun freeze(habitId: Long, date: LocalDate): Boolean = db.withTransaction {
        val habit = dao.getHabit(habitId) ?: return@withTransaction false
        val monthKey = monthKeyOf(date)
        val spent = dao.freezesSpent(habitId, monthKey)
        if (spent >= habit.freezeQuotaPerMonth) return@withTransaction false

        dao.insertFreeze(
            HabitFreezeEntity(
                habitId = habitId,
                date = date.toEpochDayInt(),
                monthKey = monthKey,
                spentAt = clock.now().toEpochMilli()
            )
        )
        // Отметка со статусом FROZEN нужна, чтобы день был виден в heatmap своим цветом
        dao.upsertEntry(
            HabitEntry(
                id = dao.entryAt(habitId, date.toEpochDayInt())?.id ?: 0,
                habitId = habitId, date = date, status = EntryStatus.FROZEN,
                completedAt = clock.now(), source = EntrySource.MANUAL
            ).toEntity()
        )
        true
    }

    override suspend fun unfreeze(habitId: Long, date: LocalDate) {
        db.withTransaction {
            dao.deleteFreeze(habitId, date.toEpochDayInt())
            val existing = dao.entryAt(habitId, date.toEpochDayInt())
            if (existing?.status == EntryStatus.FROZEN.name) {
                dao.deleteEntry(habitId, date.toEpochDayInt())
            }
        }
    }

    override fun observePauses(): Flow<List<HabitPause>> =
        dao.observePauses().map { list -> list.map { it.toDomain() } }

    override suspend fun pause(
        habitId: Long?,
        from: LocalDate,
        to: LocalDate?,
        reason: String?
    ) {
        dao.upsertPause(HabitPause(habitId = habitId, startDate = from, endDate = to, reason = reason).toEntity())
    }

    override suspend fun resumePause(pauseId: Long) = dao.deletePause(pauseId)

    override fun observeSkipReasons(): Flow<List<SkipReason>> =
        dao.observeSkipReasons().map { list -> list.map { it.toDomain() } }

    override suspend fun habitsWithReminders(): List<Habit> =
        dao.habitsWithReminders().map { it.toDomain() }

    // --- вспомогательное ----------------------------------------------------

    /** Дни, исключённые из расчёта: заморозки этой привычки и любые покрывающие паузы. */
    private fun excludedDays(
        habitId: Long,
        freezesByHabit: Map<Long, List<dev.ashwake.domain.model.habits.HabitFreeze>>,
        pauses: List<HabitPause>,
        from: LocalDate,
        to: LocalDate
    ): Set<LocalDate> {
        val result = freezesByHabit[habitId].orEmpty().map { it.date }.toMutableSet()
        pauses.filter { it.habitId == null || it.habitId == habitId }.forEach { pause ->
            var day = maxOf(pause.startDate, from)
            val last = minOf(pause.endDate ?: to, to)
            while (day <= last) {
                result += day
                day = day.plusDays(1)
            }
        }
        return result
    }

    private fun monthKeyOf(date: LocalDate): Int = date.year * 100 + date.monthValue

    /** Встроенные причины пропуска (п. 5). Заводятся один раз при первом обращении. */
    override suspend fun ensureBuiltinData() {
        if (dao.skipReasonCount() > 0) return
        dao.insertSkipReasons(
            listOf(
                HabitSkipReasonEntity(code = "FORGOT", label = "Забыл"),
                HabitSkipReasonEntity(code = "NO_TIME", label = "Не было времени"),
                HabitSkipReasonEntity(code = "NO_ENERGY", label = "Не было сил"),
                HabitSkipReasonEntity(code = "NO_WILL", label = "Не хотел"),
                HabitSkipReasonEntity(code = "CIRCUMSTANCES", label = "Обстоятельства")
            )
        )
    }
}
