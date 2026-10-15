package dev.ashwake.domain.engine.habit

import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitSchedule
import dev.ashwake.domain.model.habits.HabitScheduleType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton

data class HabitPeriodState(
    val start: LocalDate,
    val endInclusive: LocalDate,
    val target: Int,
    val completedCount: Int,
    val completed: Boolean,
    val lastCompletionDate: LocalDate?
)

/**
 * Расписание привычки: в какие дни она ожидается и чем меряется её прогресс.
 *
 * Ключевое понятие — **контрольная точка**. Для ежедневных расписаний это день,
 * для «N раз в неделю» — неделя целиком. Без такого разделения «3 раза в неделю»
 * пришлось бы считать по дням, и четыре свободных дня подряд валили бы score
 * у идеально выполненной привычки.
 */
@Singleton
class HabitScheduleResolver @Inject constructor() {

    private val HabitScheduleType.usesMultiDayPeriod: Boolean
        get() = this == HabitScheduleType.TIMES_PER_WEEK ||
            this == HabitScheduleType.EVERY_OTHER_DAY ||
            this == HabitScheduleType.BIWEEKLY

    /** Ожидается ли привычка в этот день. Для «N раз в неделю» подходит любой день. */
    fun isDue(habit: Habit, date: LocalDate): Boolean = isDue(habit.schedule, date, habit.createdAtDate)

    fun isDue(schedule: HabitSchedule, date: LocalDate, since: LocalDate?): Boolean =
        when (schedule.type) {
            HabitScheduleType.DAILY -> true

            HabitScheduleType.TIMES_PER_WEEK -> true

            HabitScheduleType.WEEKDAYS -> date.dayOfWeek in schedule.weekdays

            HabitScheduleType.EVERY_OTHER_DAY -> {
                val anchor = schedule.intervalAnchor ?: since ?: date
                Math.floorMod(ChronoUnit.DAYS.between(anchor, date), 2L) == 0L
            }

            HabitScheduleType.BIWEEKLY -> {
                val anchor = schedule.biweeklyAnchor ?: since ?: date
                val weeks = ChronoUnit.WEEKS.between(
                    anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                )
                Math.floorMod(weeks, 2L) == 0L && date.dayOfWeek == anchor.dayOfWeek
            }
        }

    /**
     * Контрольные точки в интервале.
     * Для недельных расписаний точка ставится на воскресенье — конец недели,
     * когда уже известно, набрана норма или нет.
     */
    fun checkpoints(habit: Habit, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (from > to) return emptyList()
        return when (habit.schedule.type) {
            HabitScheduleType.TIMES_PER_WEEK -> {
                val result = mutableListOf<LocalDate>()
                var monday = from.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                while (monday <= to) {
                    result += monday
                    monday = monday.plusWeeks(1)
                }
                result
            }

            HabitScheduleType.EVERY_OTHER_DAY,
            HabitScheduleType.BIWEEKLY -> {
                val length = periodLengthDays(habit)
                val result = mutableListOf<LocalDate>()
                var start = periodStart(habit, from)
                while (start <= to) {
                    result += start
                    start = start.plusDays(length)
                }
                result
            }

            else -> generateSequence(from) { it.plusDays(1) }
                .takeWhile { it <= to }
                .filter { isDue(habit, it) }
                .toList()
        }
    }

    /**
     * Сколько контрольных точек приходится на неделю.
     * Из этого числа [HabitScoreCalculator] подбирает коэффициент сглаживания,
     * чтобы редкая привычка росла не медленнее ежедневной.
     */
    fun checkpointsPerWeek(habit: Habit): Float = when (habit.schedule.type) {
        HabitScheduleType.DAILY -> 7f
        HabitScheduleType.TIMES_PER_WEEK -> 1f
        HabitScheduleType.WEEKDAYS -> habit.schedule.weekdays.count.coerceAtLeast(1).toFloat()
        HabitScheduleType.EVERY_OTHER_DAY -> 3.5f
        HabitScheduleType.BIWEEKLY -> 0.5f
    }

    /** Дни, из которых складывается значение контрольной точки. */
    fun daysOfCheckpoint(habit: Habit, checkpoint: LocalDate): List<LocalDate> =
        when (habit.schedule.type) {
            HabitScheduleType.TIMES_PER_WEEK -> {
                (0L..6L).map { checkpoint.plusDays(it) }
            }
            HabitScheduleType.EVERY_OTHER_DAY -> (0L..1L).map(checkpoint::plusDays)
            HabitScheduleType.BIWEEKLY -> (0L..13L).map(checkpoint::plusDays)
            else -> listOf(checkpoint)
        }

    fun checkpointEnd(habit: Habit, checkpoint: LocalDate): LocalDate =
        daysOfCheckpoint(habit, checkpoint).last()

    fun targetAt(habit: Habit, checkpoint: LocalDate): Int =
        if (habit.schedule.type == HabitScheduleType.TIMES_PER_WEEK) {
            habit.schedule.timesPerWeek.coerceIn(1, 7)
        } else {
            1
        }

    /** Состояние текущего срока: день, двухдневный интервал, неделя или две недели. */
    fun currentPeriodState(
        habit: Habit,
        date: LocalDate,
        entries: Map<LocalDate, dev.ashwake.domain.model.habits.HabitEntry>
    ): HabitPeriodState {
        val start = if (habit.schedule.type.usesMultiDayPeriod) periodStart(habit, date) else date
        val end = checkpointEnd(habit, start)
        val target = targetAt(habit, start)
        val completedDates = daysOfCheckpoint(habit, start)
            .asSequence()
            .filter { it <= date }
            .filter { entries[it]?.countsForStreak == true }
            .toList()
        return HabitPeriodState(
            start = start,
            endInclusive = end,
            target = target,
            completedCount = completedDates.size,
            completed = completedDates.size >= target,
            lastCompletionDate = completedDates.maxOrNull()
        )
    }

    /** Периодические привычки доступны до конца своего срока, а не один календарный день. */
    fun isActionableInCurrentPeriod(habit: Habit, date: LocalDate): Boolean =
        habit.schedule.type.usesMultiDayPeriod || isDue(habit, date)

    private fun periodStart(habit: Habit, date: LocalDate): LocalDate = when (habit.schedule.type) {
        HabitScheduleType.TIMES_PER_WEEK ->
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        HabitScheduleType.EVERY_OTHER_DAY -> alignedPeriodStart(
            anchor = habit.schedule.intervalAnchor ?: habit.createdAtDate ?: date,
            date = date,
            lengthDays = 2L
        )

        HabitScheduleType.BIWEEKLY -> alignedPeriodStart(
            anchor = habit.schedule.biweeklyAnchor ?: habit.createdAtDate ?: date,
            date = date,
            lengthDays = 14L
        )

        else -> date
    }

    private fun alignedPeriodStart(anchor: LocalDate, date: LocalDate, lengthDays: Long): LocalDate {
        val distance = ChronoUnit.DAYS.between(anchor, date)
        val period = Math.floorDiv(distance, lengthDays)
        return anchor.plusDays(period * lengthDays)
    }

    private fun periodLengthDays(habit: Habit): Long = when (habit.schedule.type) {
        HabitScheduleType.EVERY_OTHER_DAY -> 2L
        HabitScheduleType.BIWEEKLY -> 14L
        HabitScheduleType.TIMES_PER_WEEK -> 7L
        else -> 1L
    }

    /**
     * Запасная точка отсчёта для расписаний «через день» и «раз в две недели»,
     * если якорь не задан. Считается в UTC намеренно: движок обязан оставаться
     * чистым, а редактор всё равно проставляет якорь явно.
     */
    private val Habit.createdAtDate: LocalDate?
        get() = runCatching {
            createdAt.atZone(java.time.ZoneOffset.UTC).toLocalDate()
        }.getOrNull()
}
