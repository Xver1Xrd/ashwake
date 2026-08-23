package dev.ashwake.platform.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.habit.HabitScheduleResolver
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.scheduler.HabitReminderScheduler
import dev.ashwake.platform.notification.HabitReminderReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Будильники привычек.
 *
 * Смещение [REQUEST_OFFSET] отделяет коды привычек от кодов задач: без него
 * привычка с id 5 затирала бы напоминание задачи с id 5, потому что
 * PendingIntent различаются именно кодом запроса.
 */
@Singleton
class AlarmHabitReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val habits: Provider<HabitRepository>,
    private val scheduleResolver: HabitScheduleResolver,
    private val clock: AppClock
) : HabitReminderScheduler {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService()

    override fun schedule(habit: Habit) {
        val reminderTime = habit.reminderTime
        if (reminderTime == null || habit.archived) {
            cancel(habit.id)
            return
        }

        val now = clock.now().atZone(clock.zone()).toLocalDateTime()
        // Ближайший запланированный день, в который время напоминания ещё впереди
        var date: LocalDate = now.toLocalDate()
        var attempts = 0
        while (attempts < MAX_LOOKAHEAD_DAYS) {
            val candidate = LocalDateTime.of(date, reminderTime)
            if (scheduleResolver.isDue(habit, date) && candidate.isAfter(now)) {
                setAlarm(habit.id, candidate)
                return
            }
            date = date.plusDays(1)
            attempts++
        }
        cancel(habit.id)
    }

    override fun cancel(habitId: Long) {
        alarmManager?.cancel(firePendingIntent(habitId))
    }

    override fun snooze(habitId: Long, minutes: Int) {
        val at = clock.now().atZone(clock.zone()).toLocalDateTime().plusMinutes(minutes.toLong())
        setAlarm(habitId, at)
    }

    override fun scheduleAnchored(habitId: Long, delayMinutes: Int) {
        val at = clock.now().atZone(clock.zone()).toLocalDateTime()
            .plusMinutes(delayMinutes.coerceAtLeast(0).toLong())
        setAlarm(habitId, at, anchored = true)
    }

    override suspend fun rescheduleAll() {
        habits.get().habitsWithReminders().forEach(::schedule)
    }

    private fun setAlarm(habitId: Long, at: LocalDateTime, anchored: Boolean = false) {
        val manager = alarmManager ?: return
        val triggerAt = at.atZone(clock.zone()).toInstant().toEpochMilli()
        val pendingIntent = firePendingIntent(habitId, anchored)

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()
        if (canExact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun firePendingIntent(
        habitId: Long,
        anchored: Boolean = false
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        (if (anchored) ANCHOR_REQUEST_OFFSET else REQUEST_OFFSET) + habitId.toInt(),
        Intent(context, HabitReminderReceiver::class.java)
            .setAction(HabitReminderReceiver.ACTION_FIRE)
            .putExtra(HabitReminderReceiver.EXTRA_HABIT_ID, habitId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private companion object {
        const val REQUEST_OFFSET = 1_000_000

        /**
         * Якорное напоминание не должно затирать напоминание по расписанию:
         * PendingIntent различаются кодом запроса, поэтому у якорей свой
         * диапазон. Иначе привычка с якорем и временем теряла бы будильник.
         */
        const val ANCHOR_REQUEST_OFFSET = 2_000_000
        const val MAX_LOOKAHEAD_DAYS = 60
    }
}
