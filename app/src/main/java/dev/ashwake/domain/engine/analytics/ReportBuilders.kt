package dev.ashwake.domain.engine.analytics

import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** Величина с прошлым значением: без сравнения отчёт превращается в набор чисел. */
data class Trend(val current: Long, val previous: Long) {
    val delta: Long get() = current - previous
    val hasPrevious: Boolean get() = previous > 0

    /** Изменение в процентах. null, если сравнивать не с чем. */
    val percent: Int?
        get() = if (previous == 0L) null else ((delta.toDouble() / previous) * 100).roundToInt()
}

data class WeeklyReport(
    val weekStart: LocalDate,
    val weekEnd: LocalDate,
    val tasksCompleted: Trend,
    val habitMarks: Trend,
    val habitCompletionRate: Float,
    val previousCompletionRate: Float,
    val focusSeconds: Trend,
    val ritualDays: Int,
    /** Изменение score по привычкам за неделю. */
    val scoreDelta: Map<String, Float>,
    val abstinenceDays: Map<String, Long>,
    val averageMood: Float?,
    val averageEnergy: Float?
) {
    val completionRateDelta: Float get() = habitCompletionRate - previousCompletionRate
}

data class YearSummary(
    val year: Int,
    val tasksCompleted: Int,
    val habitMarks: Int,
    val longestStreak: Int,
    val longestStreakHabit: String?,
    val mostStableHabit: String?,
    val mostProblematicHabit: String?,
    val bestMonth: YearMonth?,
    val worstMonth: YearMonth?,
    val focusSeconds: Long,
    val cleanDays: Long,
    val relapseReasons: Map<String, Int>,
    /** Активность по дням года — большой heatmap. */
    val activityByDate: Map<LocalDate, Int>
) {
    val hasData: Boolean get() = tasksCompleted > 0 || habitMarks > 0
}

/**
 * Сборка отчётов (п. 10).
 *
 * Чистые классы: на вход уже посчитанные ряды, на выход — структуры для экрана.
 * Тяжёлые выборки делает репозиторий, а сравнение с прошлым периодом и выбор
 * «самой стабильной» привычки живут здесь и проверяются тестами.
 */
@Singleton
class WeeklyReportBuilder @Inject constructor() {

    fun build(
        weekStart: LocalDate,
        tasksCompleted: Long,
        tasksCompletedPrevious: Long,
        habitMarks: Long,
        habitMarksPrevious: Long,
        habitExpected: Long,
        habitExpectedPrevious: Long,
        focusSeconds: Long,
        focusSecondsPrevious: Long,
        ritualDays: Int,
        scoreDelta: Map<String, Float>,
        abstinenceDays: Map<String, Long>,
        moods: List<Int>,
        energies: List<Int>
    ): WeeklyReport = WeeklyReport(
        weekStart = weekStart,
        weekEnd = weekStart.plusDays(6),
        tasksCompleted = Trend(tasksCompleted, tasksCompletedPrevious),
        habitMarks = Trend(habitMarks, habitMarksPrevious),
        habitCompletionRate = rate(habitMarks, habitExpected),
        previousCompletionRate = rate(habitMarksPrevious, habitExpectedPrevious),
        focusSeconds = Trend(focusSeconds, focusSecondsPrevious),
        ritualDays = ritualDays,
        scoreDelta = scoreDelta,
        abstinenceDays = abstinenceDays,
        averageMood = moods.averageOrNull(),
        averageEnergy = energies.averageOrNull()
    )

    /** Доля выполнения. Нет ожидаемых отметок — нет и доли, а не ноль процентов. */
    private fun rate(done: Long, expected: Long): Float =
        if (expected <= 0) 0f else (done.toFloat() / expected).coerceIn(0f, 1f)

    private fun List<Int>.averageOrNull(): Float? =
        if (isEmpty()) null else average().toFloat()
}

@Singleton
class YearSummaryBuilder @Inject constructor() {

    /**
     * @param habitScores итоговый score по привычкам: из него выбираются
     *        самая стабильная и самая проблемная
     * @param monthlyActivity сумма событий по месяцам
     */
    fun build(
        year: Int,
        tasksCompleted: Int,
        habitMarks: Int,
        streaks: Map<String, Int>,
        habitScores: Map<String, Float>,
        monthlyActivity: Map<YearMonth, Int>,
        focusSeconds: Long,
        cleanDays: Long,
        relapseReasons: Map<String, Int>,
        activityByDate: Map<LocalDate, Int>
    ): YearSummary {
        val bestStreak = streaks.maxByOrNull { it.value }

        // «Самая проблемная» выбирается только среди привычек, которые вообще
        // вели: привычка, заведённая вчера, не должна получать этот титул
        val ranked = habitScores.filterValues { it > 0f }

        return YearSummary(
            year = year,
            tasksCompleted = tasksCompleted,
            habitMarks = habitMarks,
            longestStreak = bestStreak?.value ?: 0,
            longestStreakHabit = bestStreak?.key,
            mostStableHabit = ranked.maxByOrNull { it.value }?.key,
            mostProblematicHabit = ranked.minByOrNull { it.value }?.key,
            bestMonth = monthlyActivity.maxByOrNull { it.value }?.key,
            worstMonth = monthlyActivity.filterValues { it > 0 }.minByOrNull { it.value }?.key,
            focusSeconds = focusSeconds,
            cleanDays = cleanDays,
            relapseReasons = relapseReasons.toList()
                .sortedByDescending { it.second }
                .toMap(),
            activityByDate = activityByDate
        )
    }
}
