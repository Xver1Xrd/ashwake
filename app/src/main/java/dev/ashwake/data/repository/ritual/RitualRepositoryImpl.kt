package dev.ashwake.data.repository.ritual

import androidx.room.withTransaction
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.db.AshwakeDatabase
import dev.ashwake.data.db.dao.habits.HabitDao
import dev.ashwake.data.db.dao.ritual.RitualDao
import dev.ashwake.data.db.dao.routines.FocusDao
import dev.ashwake.data.db.entity.ritual.DailyReviewEntity
import dev.ashwake.data.db.entity.ritual.DailyReviewTopTaskEntity
import dev.ashwake.data.db.mapper.tasks.toEpochDayInt
import dev.ashwake.data.db.mapper.tasks.toLocalDate
import dev.ashwake.domain.engine.analytics.CorrelationAnalyzer
import dev.ashwake.domain.engine.analytics.CorrelationReport
import dev.ashwake.domain.engine.analytics.WeeklyReport
import dev.ashwake.domain.engine.analytics.WeeklyReportBuilder
import dev.ashwake.domain.engine.analytics.YearSummary
import dev.ashwake.domain.engine.analytics.YearSummaryBuilder
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.ritual.DailyReview
import dev.ashwake.domain.model.ritual.ReviewCompletion
import dev.ashwake.domain.repository.abstinence.AbstinenceRepository
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.repository.ritual.RitualRepository
import dev.ashwake.domain.repository.ritual.RitualState
import dev.ashwake.domain.repository.routines.FocusRepository
import dev.ashwake.domain.repository.tasks.TaskFilter
import dev.ashwake.domain.repository.tasks.TaskRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** До этого часа утром ещё можно пройти вчерашний ритуал. */
private const val CATCH_UP_UNTIL_HOUR = 12

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class RitualRepositoryImpl @Inject constructor(
    private val db: AshwakeDatabase,
    private val dao: RitualDao,
    private val habitDao: HabitDao,
    private val focusDao: FocusDao,
    private val tasks: TaskRepository,
    private val habits: HabitRepository,
    private val abstinences: AbstinenceRepository,
    private val focus: FocusRepository,
    private val analyzer: CorrelationAnalyzer,
    private val weeklyBuilder: WeeklyReportBuilder,
    private val yearBuilder: YearSummaryBuilder,
    private val clock: AppClock
) : RitualRepository {

    override fun observeRitual(date: LocalDate): Flow<RitualState> = combine(
        dao.observeReview(date.toEpochDayInt()),
        tasks.observeTasksInRange(date.minusDays(30), date, includeDone = false),
        habits.observeHabitsWithProgress(date)
    ) { review, openTasks, habitList ->
        RitualState(
            date = date,
            review = review?.toDomain(),
            // Незакрытыми считаются и просроченные: их тоже нужно разобрать
            openTasks = openTasks.filterNot { it.isDone },
            unmarkedHabits = habitList.filter { it.dueToday && !it.paused && it.todayEntry == null },
            tomorrowCandidates = openTasks.filterNot { it.isDone },
            isCatchUp = date < clock.today()
        )
    }

    /**
     * Ритуал утром относится ко вчерашнему дню, если вчерашний ещё не пройден.
     * Иначе человек, проснувшийся раньше, чем успел оценить вчера,
     * потерял бы день безвозвратно.
     */
    override suspend fun currentRitualDate(): LocalDate {
        val today = clock.today()
        val hour = clock.now().atZone(clock.zone()).hour
        if (hour >= CATCH_UP_UNTIL_HOUR) return today

        val yesterday = today.minusDays(1)
        return if (dao.review(yesterday.toEpochDayInt()) == null) yesterday else today
    }

    override suspend fun saveReview(
        date: LocalDate,
        dayRating: Int?,
        mood: Int?,
        energy: Int?,
        note: String?,
        topTaskIds: List<Long>,
        completedAs: ReviewCompletion
    ) {
        db.withTransaction {
            dao.upsertReview(
                DailyReviewEntity(
                    date = date.toEpochDayInt(),
                    dayRating = dayRating,
                    mood = mood,
                    energy = energy,
                    note = note?.takeIf { it.isNotBlank() },
                    completedAt = clock.now().toEpochMilli(),
                    completedAs = completedAs.name
                )
            )
            dao.clearTopTasks(date.toEpochDayInt())
            dao.insertTopTasks(
                topTaskIds.take(TOP_TASKS_LIMIT).mapIndexed { index, taskId ->
                    DailyReviewTopTaskEntity(date.toEpochDayInt(), taskId, index)
                }
            )
        }
    }

    override fun observeReviews(from: LocalDate, to: LocalDate): Flow<List<DailyReview>> =
        dao.observeReviews(from.toEpochDayInt(), to.toEpochDayInt())
            .map { list -> list.map { it.toDomain() } }

    // --- аналитика ---------------------------------------------------------

    override suspend fun correlations(windowDays: Long): CorrelationReport {
        val today = clock.today()
        val from = today.minusDays(windowDays)

        val reviews = dao.reviews(from.toEpochDayInt(), today.toEpochDayInt())
        val habitList = habits.observeHabits().first()
        val entries = habitDao
            .observeEntriesInRange(from.toEpochDayInt(), today.toEpochDayInt())
            .first()

        val habitDays = entries
            .filter { it.status == EntryStatus.DONE.name || it.status == EntryStatus.MINIMUM.name }
            .groupBy { it.habitId }
            .mapValues { (_, list) -> list.map { it.date.toLocalDate() }.toSet() }

        return analyzer.analyze(
            habitNames = habitList.associate { it.id to it.name },
            habitDays = habitDays,
            moodByDate = reviews.mapNotNull { row ->
                row.mood?.let { row.date.toLocalDate() to it }
            }.toMap(),
            energyByDate = reviews.mapNotNull { row ->
                row.energy?.let { row.date.toLocalDate() to it }
            }.toMap()
        )
    }

    override suspend fun weeklyReport(weekStart: LocalDate): WeeklyReport {
        val weekEnd = weekStart.plusDays(6)
        val previousStart = weekStart.minusWeeks(1)

        val current = weekStats(weekStart, weekEnd)
        val previous = weekStats(previousStart, previousStart.plusDays(6))
        val reviews = dao.reviews(weekStart.toEpochDayInt(), weekEnd.toEpochDayInt())

        val abstinenceDays = abstinences.observeAll(clock.now()).first()
            .associate { it.abstinence.name to it.stats.currentDays }

        return weeklyBuilder.build(
            weekStart = weekStart,
            tasksCompleted = current.tasksDone,
            tasksCompletedPrevious = previous.tasksDone,
            habitMarks = current.habitMarks,
            habitMarksPrevious = previous.habitMarks,
            habitExpected = current.habitExpected,
            habitExpectedPrevious = previous.habitExpected,
            focusSeconds = current.focusSeconds,
            focusSecondsPrevious = previous.focusSeconds,
            ritualDays = reviews.size,
            scoreDelta = emptyMap(),
            abstinenceDays = abstinenceDays,
            moods = reviews.mapNotNull { it.mood },
            energies = reviews.mapNotNull { it.energy }
        )
    }

    private data class WeekStats(
        val tasksDone: Long,
        val habitMarks: Long,
        val habitExpected: Long,
        val focusSeconds: Long
    )

    private suspend fun weekStats(from: LocalDate, to: LocalDate): WeekStats {
        val entries = habitDao.observeEntriesInRange(from.toEpochDayInt(), to.toEpochDayInt())
            .first()
        val marks = entries.count {
            it.status == EntryStatus.DONE.name || it.status == EntryStatus.MINIMUM.name
        }.toLong()

        val doneTasks = tasks.observeTasks(TaskFilter(includeDone = true)).first()
            .count { task ->
                task.isDone && task.dueDate != null && task.dueDate in from..to
            }.toLong()

        // Ожидаемые отметки: активные привычки на каждый день недели.
        // Точный расчёт по расписанию делает движок привычек, здесь достаточно
        // верхней оценки — доля выполнения нужна как тенденция, а не как цифра до сотых
        val activeHabits = habits.observeHabits().first().size
        val expected = activeHabits.toLong() * 7

        val focusSeconds = focus.observeStats(sinceDays = 365).first().let { stats ->
            stats.byWeekday.values.sum()
        }

        return WeekStats(doneTasks, marks, expected, focusSeconds)
    }

    override suspend fun yearSummary(year: Int): YearSummary {
        val from = LocalDate.of(year, 1, 1)
        val to = LocalDate.of(year, 12, 31)

        val entries = habitDao.observeEntriesInRange(from.toEpochDayInt(), to.toEpochDayInt())
            .first()
        val marks = entries.filter {
            it.status == EntryStatus.DONE.name || it.status == EntryStatus.MINIMUM.name
        }

        val progress = habits.observeHabitsWithProgress(clock.today()).first()
        val doneTasks = tasks.observeTasks(TaskFilter(includeDone = true)).first()
            .count { it.isDone && it.dueDate != null && it.dueDate.year == year }

        val activityByDate = marks
            .groupBy { it.date.toLocalDate() }
            .mapValues { (_, list) -> list.size }

        val abstinenceStats = abstinences.observeAll(clock.now()).first()

        return yearBuilder.build(
            year = year,
            tasksCompleted = doneTasks,
            habitMarks = marks.size,
            streaks = progress.associate { it.habit.name to it.recordStreak },
            habitScores = progress.associate { it.habit.name to it.score },
            monthlyActivity = activityByDate.entries
                .groupBy { YearMonth.from(it.key) }
                .mapValues { (_, list) -> list.sumOf { it.value } },
            focusSeconds = focus.observeStats(sinceDays = 365).first().totalSeconds,
            cleanDays = abstinenceStats.sumOf { it.stats.totalCleanDays },
            relapseReasons = emptyMap(),
            activityByDate = activityByDate
        )
    }

    private fun DailyReviewEntity.toDomain() = DailyReview(
        date = date.toLocalDate(),
        dayRating = dayRating,
        mood = mood,
        energy = energy,
        note = note,
        completedAt = java.time.Instant.ofEpochMilli(completedAt),
        completedAs = ReviewCompletion.valueOf(completedAs)
    )

    private companion object {
        const val TOP_TASKS_LIMIT = 3
    }
}
