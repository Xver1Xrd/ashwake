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

    /**
     * Напоминание по якорю: через [delayMinutes] после того, как сработало
     * событие-триггер. Отдельно от [schedule], потому что живёт по своему
     * коду запроса и не должно затирать напоминание по расписанию.
     */
    fun scheduleAnchored(habitId: Long, delayMinutes: Int)

    suspend fun rescheduleAll()
}
