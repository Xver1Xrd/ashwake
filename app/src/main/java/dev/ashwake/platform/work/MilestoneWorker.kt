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
import dev.ashwake.domain.repository.abstinence.AbstinenceRepository
import dev.ashwake.platform.notification.AshwakeNotifications
import java.util.concurrent.TimeUnit

/**
 * Проверка достигнутых вех отказов (п. 4).
 *
 * Периодический воркер, а не будильник на каждую веху: вех у каждого отказа
 * полтора десятка, и точность до секунды здесь не нужна — «30 дней» одинаково
 * верно и в полночь, и в восемь утра. Один воркер вместо сотни alarm'ов.
 */
@HiltWorker
class MilestoneWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val abstinences: AbstinenceRepository,
    private val clock: AppClock
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val reached = abstinences.collectReachedMilestones(clock.now())
        reached.forEach { (abstinence, milestone) ->
            notify(
                id = (abstinence.id * 100 + milestone.days).toInt(),
                title = "${abstinence.name}: ${milestone.title}",
                // Текст к вехе пишет сам пользователь; своего приложение не добавляет
                text = milestone.userText ?: "Веха достигнута"
            )
        }
        return Result.success()
    }

    private fun notify(id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification =
            NotificationCompat.Builder(context, AshwakeNotifications.CHANNEL_ABSTINENCE)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()

        context.getSystemService<NotificationManager>()?.notify(id, notification)
    }

    companion object {
        private const val UNIQUE_NAME = "milestone-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MilestoneWorker>(6, TimeUnit.HOURS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
