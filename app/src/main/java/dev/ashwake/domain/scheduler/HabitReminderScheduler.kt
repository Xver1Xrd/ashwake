package dev.ashwake.domain.scheduler

import dev.ashwake.domain.model.habits.Habit

/**
 * Напоминания об отметке привычки (п. 3).
 *
 * В отличие от задач, привычка повторяется бесконечно, поэтому будильник
 * ставится на ближайший запланированный день с учётом расписания:
 * привычка «по понедельникам и четвергам» не должна звонить в среду.
 */
interface HabitReminderScheduler {

    fun schedule(habit: Habit)

    fun cancel(habitId: Long)

    /** Отложить на час — кнопка «Через час» в уведомлении. */
    fun snooze(habitId: Long, minutes: Int = 60)

    suspend fun rescheduleAll()
}
