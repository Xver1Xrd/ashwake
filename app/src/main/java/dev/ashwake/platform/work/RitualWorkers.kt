package dev.ashwake.platform.work

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.ashwake.MainActivity
import dev.ashwake.R
import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.repository.ritual.RitualRepository
import dev.ashwake.platform.notification.AshwakeNotifications
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Напоминание о вечернем ритуале (п. 9).
 *
 * Воркер проверяет раз в час и напоминает только вечером и только если ритуал
 * за сегодня ещё не пройден: точный будильник здесь избыточен, а лишнее
 * уведомление «вы уже всё сделали» раздражает сильнее пропущенного.
 */
@HiltWorker
class RitualReminderWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val ritual: RitualRepository,
    private val clock: AppClock
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val hour = clock.now().atZone(clock.zone()).hour
        if (hour !in REMIND_FROM_HOUR..REMIND_UNTIL_HOUR) return Result.success()

        val date = ritual.currentRitualDate()
        // Уже пройден — молчим: уведомление «вы уже всё сделали» раздражает
        // сильнее, чем пропущенное напоминание
        if (ritual.observeRitual(date).first().alreadyDone) return Result.success()

        notify(
            title = "Вечерний ритуал",
            text = "Две минуты: оценить день, разобрать хвосты, наметить завтра"
        )
        return Result.success()
    }

    private fun notify(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, AshwakeNotifications.CHANNEL_TASKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        context.getSystemService<NotificationManager>()?.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val UNIQUE_NAME = "ritual-reminder"
        private const val NOTIFICATION_ID = 5150
        private const val REMIND_FROM_HOUR = 20
        private const val REMIND_UNTIL_HOUR = 23

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<RitualReminderWorker>(1, TimeUnit.HOURS).build()
            )
        }
    }
}

/** Недельный отчёт: считается воскресным вечером (п. 10). */
@HiltWorker
class WeeklyReportWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val ritual: RitualRepository,
    private val clock: AppClock
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val today = clock.today()
        if (today.dayOfWeek != DayOfWeek.SUNDAY) return Result.success()

        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val report = ritual.weeklyReport(weekStart)

        notify(
            "Итоги недели",
            "Задачи ${report.tasksCompleted.current}, привычки " +
                "${(report.habitCompletionRate * 100).toInt()}%, " +
                "ритуал ${report.ritualDays} из 7"
        )
        return Result.success()
    }

    private fun notify(title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        context.getSystemService<NotificationManager>()?.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, AshwakeNotifications.CHANNEL_TASKS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
        )
    }

    companion object {
        private const val UNIQUE_NAME = "weekly-report"
        private const val NOTIFICATION_ID = 5151

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WeeklyReportWorker>(12, TimeUnit.HOURS).build()
            )
        }
    }
}
