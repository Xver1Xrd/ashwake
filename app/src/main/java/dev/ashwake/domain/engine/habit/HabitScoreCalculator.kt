package dev.ashwake.domain.engine.habit

import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitEntry
import dev.ashwake.domain.model.habits.HabitScheduleType
import dev.ashwake.domain.model.habits.HabitType
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

data class ScorePoint(val date: LocalDate, val score: Float)

/**
 * Habit score вместо голого стрика (п. 3).
 *
 * Экспоненциальное сглаживание: `score = score * (1 - a) + value * a`.
 * Коэффициент `a` подобран так, чтобы при идеальном выполнении score дошёл
 * до [TARGET_SCORE] примерно за [TARGET_DAYS] дней. Из уравнения
 * `1 - (1 - a)^n = TARGET_SCORE` при `n = TARGET_DAYS` получается
 * `a = 1 - (1 - TARGET_SCORE)^(1 / n)`.
 *
 * Для неежедневных привычек контрольных точек за месяц меньше, поэтому `n`
 * масштабируется по частоте: привычка «3 раза в неделю» растёт за месяц так же,
 * как ежедневная, а не втрое медленнее.
 *
 * Смысл всей конструкции: несколько пропусков после длинной серии проседают score
 * на единицы процентов, а не обнуляют прогресс, как классический стрик.
 */
@Singleton
class HabitScoreCalculator @Inject constructor(
    private val scheduleResolver: HabitScheduleResolver
) {

    /** Коэффициент сглаживания для заданного числа контрольных точек в неделю. */
    fun alphaFor(checkpointsPerWeek: Float): Double {
        val perWeek = checkpointsPerWeek.coerceAtLeast(MIN_CHECKPOINTS_PER_WEEK).toDouble()
        val checkpointsInWindow = TARGET_DAYS / 7.0 * perWeek
        return 1.0 - (1.0 - TARGET_SCORE).pow(1.0 / checkpointsInWindow)
    }

    /**
     * Ряд значений score по контрольным точкам интервала.
     *
     * @param entries отметки по датам
     * @param excludedDays заморозки и паузы: такие точки пропускаются целиком,
     *        а не считаются нулём — в этом весь смысл заморозки
     * @param initialScore значение на входе в интервал (для досчёта поверх снимка)
     */
    fun computeSeries(
        habit: Habit,
        entries: Map<LocalDate, HabitEntry>,
        excludedDays: Set<LocalDate> = emptySet(),
        from: LocalDate,
        to: LocalDate,
        initialScore: Float = 0f
    ): List<ScorePoint> {
        val alpha = alphaFor(scheduleResolver.checkpointsPerWeek(habit))
        var score = initialScore.toDouble()
        val result = mutableListOf<ScorePoint>()

        scheduleResolver.checkpoints(habit, from, to).forEach { checkpoint ->
            val days = scheduleResolver.daysOfCheckpoint(habit, checkpoint)
            if (days.all { it in excludedDays || entries[it]?.isExcluded == true }) {
                // Полностью замороженная или паузная точка: score стоит на месте
                result += ScorePoint(checkpoint, score.toFloat())
                return@forEach
            }
            val value = valueOf(habit, days, entries, excludedDays)
            score = score * (1 - alpha) + value * alpha
            result += ScorePoint(checkpoint, score.toFloat())
        }
        return result
    }

    fun currentScore(
        habit: Habit,
        entries: Map<LocalDate, HabitEntry>,
        excludedDays: Set<LocalDate> = emptySet(),
        from: LocalDate,
        to: LocalDate,
        initialScore: Float = 0f
    ): Float = computeSeries(habit, entries, excludedDays, from, to, initialScore)
        .lastOrNull()?.score ?: initialScore

    /**
     * Значение контрольной точки в диапазоне 0..1.
     *
     * Для «N раз в неделю» это доля набранной недельной нормы, для остальных —
     * вклад дня. У негативной привычки логика обратная: день без записи о срыве
     * и есть успех, поэтому отсутствие отметки даёт единицу.
     */
    private fun valueOf(
        habit: Habit,
        days: List<LocalDate>,
        entries: Map<LocalDate, HabitEntry>,
        excludedDays: Set<LocalDate>
    ): Double {
        if (habit.schedule.type == HabitScheduleType.TIMES_PER_WEEK) {
            val target = habit.schedule.timesPerWeek.coerceAtLeast(1)
            val done = days.sumOf { day ->
                entries[day]?.takeIf { !it.isExcluded }?.contribution?.toDouble() ?: 0.0
            }
            return (done / target).coerceIn(0.0, 1.0)
        }

        val day = days.first()
        val entry = entries[day]

        if (habit.type == HabitType.NEGATIVE) {
            // Отметка у негативной привычки означает срыв
            return when {
                day in excludedDays -> 1.0
                entry == null -> 1.0
                entry.isExcluded -> 1.0
                entry.status == EntryStatus.SKIPPED -> 0.0
                else -> 0.0
            }
        }

        return entry?.contribution?.toDouble() ?: 0.0
    }

    companion object {
        /** Целевое значение score при идеальном выполнении за [TARGET_DAYS]. */
        const val TARGET_SCORE = 0.8
        const val TARGET_DAYS = 30.0

        /** Реже раза в две недели коэффициент уже не масштабируем: рост стал бы скачкообразным. */
        const val MIN_CHECKPOINTS_PER_WEEK = 0.5f
    }
}
