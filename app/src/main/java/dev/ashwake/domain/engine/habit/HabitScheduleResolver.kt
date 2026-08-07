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
                var sunday = from.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                while (sunday <= to) {
                    result += sunday
                    sunday = sunday.plusWeeks(1)
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
                val monday = checkpoint.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                (0L..6L).map { monday.plusDays(it) }
            }
            else -> listOf(checkpoint)
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
