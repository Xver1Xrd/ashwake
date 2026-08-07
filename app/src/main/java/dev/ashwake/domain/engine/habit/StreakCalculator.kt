package dev.ashwake.domain.engine.habit

import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitEntry
import dev.ashwake.domain.model.habits.HabitType
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class StreakResult(val current: Int, val record: Int)

/**
 * Стрик рядом со score, а не вместо него (п. 3).
 *
 * Правила, из-за которых это отдельный класс, а не два цикла в репозитории:
 * - незапланированные дни серию не рвут и в неё не входят;
 * - заморозка и пауза — тоже пропуск без разрыва (п. 5);
 * - «минимум» серию сохраняет наравне с полным выполнением;
 * - сегодняшний день, ещё не отмеченный, серию не обрывает — она просто
 *   считается до вчера, иначе стрик обнулялся бы каждое утро.
 */
@Singleton
class StreakCalculator @Inject constructor(
    private val scheduleResolver: HabitScheduleResolver
) {

    fun compute(
        habit: Habit,
        entries: Map<LocalDate, HabitEntry>,
        excludedDays: Set<LocalDate> = emptySet(),
        from: LocalDate,
        today: LocalDate
    ): StreakResult {
        val checkpoints = scheduleResolver.checkpoints(habit, from, today)
        if (checkpoints.isEmpty()) return StreakResult(0, 0)

        var running = 0
        var record = 0
        var current = 0

        checkpoints.forEach { checkpoint ->
            when (statusOf(habit, checkpoint, entries, excludedDays, today)) {
                CheckpointOutcome.SUCCESS -> {
                    running++
                    if (running > record) record = running
                    current = running
                }
                CheckpointOutcome.NEUTRAL -> Unit  // заморозка, пауза, сегодняшний хвост
                CheckpointOutcome.FAILURE -> {
                    running = 0
                    current = 0
                }
            }
        }
        return StreakResult(current, record)
    }

    private enum class CheckpointOutcome { SUCCESS, FAILURE, NEUTRAL }

    private fun statusOf(
        habit: Habit,
        checkpoint: LocalDate,
        entries: Map<LocalDate, HabitEntry>,
        excludedDays: Set<LocalDate>,
        today: LocalDate
    ): CheckpointOutcome {
        val days = scheduleResolver.daysOfCheckpoint(habit, checkpoint)

        if (days.all { it in excludedDays || entries[it]?.isExcluded == true }) {
            return CheckpointOutcome.NEUTRAL
        }

        if (habit.schedule.type == dev.ashwake.domain.model.habits.HabitScheduleType.TIMES_PER_WEEK) {
            val target = habit.schedule.timesPerWeek.coerceAtLeast(1)
            val done = days.count { entries[it]?.countsForStreak == true }
            // Незакрытая текущая неделя ещё не провал
            if (checkpoint >= today && done < target) return CheckpointOutcome.NEUTRAL
            return if (done >= target) CheckpointOutcome.SUCCESS else CheckpointOutcome.FAILURE
        }

        val entry = entries[checkpoint]

        if (habit.type == HabitType.NEGATIVE) {
            // Успех — отсутствие срыва. Сегодняшний день ещё не закончился,
            // но и срыва в нём нет, поэтому он честно считается успешным.
            return if (entry == null || entry.isExcluded) CheckpointOutcome.SUCCESS
            else CheckpointOutcome.FAILURE
        }

        return when {
            entry?.countsForStreak == true -> CheckpointOutcome.SUCCESS
            checkpoint >= today -> CheckpointOutcome.NEUTRAL
            else -> CheckpointOutcome.FAILURE
        }
    }
}
