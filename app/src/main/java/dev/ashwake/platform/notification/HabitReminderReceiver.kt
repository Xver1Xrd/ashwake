package dev.ashwake.platform.notification

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.AndroidEntryPoint
import dev.ashwake.MainActivity
import dev.ashwake.R
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.model.habits.EntrySource
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.repository.habits.HabitRepository
import dev.ashwake.domain.scheduler.HabitReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Отметка привычки прямо из шторки: «Выполнено» / «Пропустить» / «Через час» (п. 3).
 *
 * После любого действия сразу ставится будильник на следующий запланированный день —
 * иначе цепочка обрывается на первом же напоминании.
 */
@AndroidEntryPoint
class HabitReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var scheduler: HabitReminderScheduler
    @Inject lateinit var clock: AppClock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getLongExtra(EXTRA_HABIT_ID, 0L)
        if (habitId == 0L) return
        val action = intent.action ?: return
        val pendingResult = goAsync()

        scope.launch {
            try {
                val habit = habits.getHabit(habitId) ?: return@launch
                when (action) {
                    ACTION_FIRE -> {
                        showNotification(context, habitId, habit.name)
                        scheduler.schedule(habit)
                    }

                    ACTION_DONE -> {
                        habits.mark(
                            habitId, clock.today(), EntryStatus.DONE,
                            source = EntrySource.NOTIFICATION
                        )
                        cancelNotification(context, habitId)
                        scheduler.schedule(habit)
                    }

                    ACTION_SKIP -> {
                        habits.mark(
                            habitId, clock.today(), EntryStatus.SKIPPED,
                            source = EntrySource.NOTIFICATION
                        )
                        cancelNotification(context, habitId)
                        scheduler.schedule(habit)
                    }

                    ACTION_SNOOZE -> {
                        scheduler.snooze(habitId)
                        cancelNotification(context, habitId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, habitId: Long, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_OFFSET + habitId.toInt(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, AshwakeNotifications.CHANNEL_HABITS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(name)
            .setContentText("Отметить за сегодня")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Выполнено", actionIntent(context, habitId, ACTION_DONE, 1))
            .addAction(0, "Пропустить", actionIntent(context, habitId, ACTION_SKIP, 2))
            .addAction(0, "Через час", actionIntent(context, habitId, ACTION_SNOOZE, 3))
            .build()

        context.getSystemService<NotificationManager>()
            ?.notify(notificationId(habitId), notification)
    }

    private fun actionIntent(
        context: Context,
        habitId: Long,
        action: String,
        offset: Int
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_OFFSET + habitId.toInt() * REQUEST_STRIDE + offset,
        Intent(context, HabitReminderReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_HABIT_ID, habitId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun cancelNotification(context: Context, habitId: Long) {
        context.getSystemService<NotificationManager>()?.cancel(notificationId(habitId))
    }

    companion object {
        const val ACTION_FIRE = "dev.ashwake.action.HABIT_REMINDER_FIRE"
        const val ACTION_DONE = "dev.ashwake.action.HABIT_DONE"
        const val ACTION_SKIP = "dev.ashwake.action.HABIT_SKIP"
        const val ACTION_SNOOZE = "dev.ashwake.action.HABIT_SNOOZE"
        const val EXTRA_HABIT_ID = "habitId"

        private const val REQUEST_OFFSET = 2_000_000
        private const val REQUEST_STRIDE = 4

        fun notificationId(habitId: Long): Int = (REQUEST_OFFSET + habitId).toInt()
    }
}
