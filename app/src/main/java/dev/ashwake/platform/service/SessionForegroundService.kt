package dev.ashwake.platform.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.ashwake.MainActivity
import dev.ashwake.R
import dev.ashwake.domain.engine.focus.phaseTitle
import dev.ashwake.platform.notification.AshwakeNotifications
import dev.ashwake.platform.session.FocusController
import dev.ashwake.platform.session.RoutineRunController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground-сервис таймеров рутины и фокуса.
 *
 * Сервис намеренно тонкий: он только держит процесс живым и рисует
 * уведомление. Само состояние живёт в контроллерах-синглтонах, поэтому
 * пересоздание сервиса не сбрасывает таймер, а закрытие экрана его не трогает.
 */
@AndroidEntryPoint
class SessionForegroundService : Service() {

    @Inject lateinit var routineController: RoutineRunController
    @Inject lateinit var focusController: FocusController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundSafely(buildNotification("Ashwake", "Таймер запущен"))

        scope.launch {
            combine(routineController.state, focusController.state) { routine, focus ->
                when {
                    routine.active -> {
                        val step = routine.currentStep?.title ?: "Рутина"
                        val remaining = routine.progress.stepRemainingSeconds
                        (routine.routine?.name ?: "Рутина") to
                            "$step · ${formatTime(remaining)}"
                    }

                    focus.active -> phaseTitle(focus.phase) to when {
                        focus.mode == dev.ashwake.domain.model.focus.FocusMode.STOPWATCH ->
                            formatTime(focus.elapsedSeconds)
                        else -> formatTime(focus.remainingSeconds) +
                            (focus.taskTitle?.let { " · $it" } ?: "")
                    }

                    else -> null
                }
            }.collect { content ->
                if (content == null) {
                    stopSelf()
                    return@collect
                }
                notify(buildNotification(content.first, content.second))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundSafely(notification: Notification) {
        // На API 34+ тип foreground-сервиса обязателен, иначе система его убьёт
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notify(notification: Notification) {
        androidx.core.app.NotificationManagerCompat.from(this)
            .let { manager ->
                runCatching { manager.notify(NOTIFICATION_ID, notification) }
            }
    }

    private fun buildNotification(title: String, text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, AshwakeNotifications.CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 4242

        fun start(context: Context) {
            val intent = Intent(context, SessionForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionForegroundService::class.java))
        }
    }
}

internal fun formatTime(seconds: Int): String {
    val safe = seconds.coerceAtLeast(0)
    val minutes = safe / 60
    return if (minutes >= 60) {
        "%d:%02d:%02d".format(minutes / 60, minutes % 60, safe % 60)
    } else {
        "%02d:%02d".format(minutes, safe % 60)
    }
}
