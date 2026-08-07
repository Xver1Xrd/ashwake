package dev.ashwake.platform.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.model.tasks.TaskStatus
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.domain.scheduler.TaskReminderScheduler
import dev.ashwake.platform.notification.TaskReminderReceiver
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Реализация напоминаний на AlarmManager.
 *
 * Репозиторий приходит через [Provider]: планировщик нужен use case'ам,
 * а те — ресиверу, который сам инжектит репозиторий. Ленивое разрешение
 * убирает риск цикла в графе Hilt при добавлении новых фич.
 */
@Singleton
class AlarmTaskReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tasks: Provider<TaskRepository>,
    private val clock: AppClock
) : TaskReminderScheduler {

    private val alarmManager: AlarmManager?
        get() = context.getSystemService()

    override fun schedule(task: Task) {
        val triggerAt = nextTriggerMillis(task)
        if (triggerAt == null) {
            cancel(task.id)
            return
        }
        val manager = alarmManager ?: return
        val pendingIntent = firePendingIntent(task.id)

        // На API 31+ точные будильники требуют отдельного разрешения. Если его нет,
        // ставим неточный: лучше напоминание с задержкой, чем молчание.
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()
        if (canExact) {
            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    override fun cancel(taskId: Long) {
        alarmManager?.cancel(firePendingIntent(taskId))
    }

    override suspend fun rescheduleAll() {
        tasks.get().tasksWithReminders().forEach(::schedule)
    }

    /**
     * Момент следующего звонка или null, если напоминать нечего.
     *
     * Если плановое время уже прошло, обычная задача молчит — догонять просроченный
     * будильник смысла нет. Настойчивая, наоборот, продолжает цепочку с шагом N минут,
     * потому что именно в этом её смысл.
     */
    private fun nextTriggerMillis(task: Task): Long? {
        if (task.status != TaskStatus.ACTIVE || task.isTemplate) return null
        val date = task.dueDate ?: return null
        val time = task.dueTime ?: return null

        val planned = LocalDateTime.of(date, time)
            .atZone(clock.zone()).toInstant().toEpochMilli()
        val now = clock.now().toEpochMilli()
        if (planned > now) return planned

        val repeatMinutes = task.persistentReminderMinutes ?: return null
        if (repeatMinutes <= 0) return null
        return now + repeatMinutes * 60_000L
    }

    private fun firePendingIntent(taskId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        taskId.toInt(),
        Intent(context, TaskReminderReceiver::class.java)
            .setAction(TaskReminderReceiver.ACTION_FIRE)
            .putExtra(TaskReminderReceiver.EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
