package dev.ashwake.platform.blocking

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
import dev.ashwake.domain.engine.blocking.BlockingEvaluator
import dev.ashwake.domain.repository.blocking.BlockingRepository
import dev.ashwake.platform.notification.AshwakeNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Сервис блокировки приложений (п. 7).
 *
 * Опрашивает передний план раз в секунду и поднимает оверлей, когда открыто
 * заблокированное приложение. Опрос, а не подписка, — цена выбора
 * `UsageStatsManager` вместо `AccessibilityService`: событий система не шлёт.
 *
 * Постоянное уведомление обязательно и по требованиям Android, и по-честному:
 * сервис, который следит за тем, какие приложения открываются, не должен
 * работать незаметно.
 */
@AndroidEntryPoint
class BlockingService : Service() {

    @Inject lateinit var monitor: ForegroundAppMonitor
    @Inject lateinit var overlay: BlockingOverlay
    @Inject lateinit var blocking: BlockingRepository
    @Inject lateinit var evaluator: BlockingEvaluator

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var lastPackage: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundSafely()
        scope.launch { monitorLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        withMain { overlay.hide() }
        super.onDestroy()
    }

    private suspend fun monitorLoop() {
        while (true) {
            delay(POLL_INTERVAL_MILLIS)

            val current = monitor.currentForegroundPackage()
            if (current == null || current == packageName) {
                if (overlay.isShowing) withContext(Dispatchers.Main) { overlay.hide() }
                continue
            }

            // Смена приложения сбрасывает оверлей: вердикт для нового пакета
            // считается заново, иначе он висел бы поверх невиновного приложения
            if (current != lastPackage && overlay.isShowing) {
                withContext(Dispatchers.Main) { overlay.hide() }
            }
            lastPackage = current

            val verdict = blocking.verdictFor(current)
            if (!verdict.blocked) {
                if (overlay.isShowing) withContext(Dispatchers.Main) { overlay.hide() }
                continue
            }
            if (overlay.isShowing) continue
            if (!monitor.hasOverlayPermission()) continue

            withContext(Dispatchers.Main) {
                overlay.show(
                    verdict = verdict,
                    onOpenApp = {
                        overlay.hide()
                        startActivity(
                            Intent(this@BlockingService, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onBypass = {
                        overlay.hide()
                        scope.launch {
                            blocking.registerBypass(
                                packageName = current,
                                ruleId = verdict.ruleId,
                                delaySeconds = evaluator.bypassDelaySeconds
                            )
                        }
                    }
                )
            }
            runCountdown()
        }
    }

    /** Обратный отсчёт перед тем, как кнопка обхода станет активной. */
    private suspend fun runCountdown() {
        for (second in evaluator.bypassDelaySeconds downTo 0) {
            if (!overlay.isShowing) return
            withContext(Dispatchers.Main) { overlay.updateCountdown(second) }
            delay(1_000)
        }
    }

    private fun withMain(block: () -> Unit) {
        scope.launch { withContext(Dispatchers.Main) { block() } }
    }

    private fun startForegroundSafely() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, AshwakeNotifications.CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Блокировка приложений включена")
            .setContentText("Пока не выполнены условия разблокировки")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 7373
        /** Секунда: чаще опрашивать бессмысленно, реже — заметна задержка. */
        private const val POLL_INTERVAL_MILLIS = 1_000L

        fun start(context: Context) {
            val intent = Intent(context, BlockingService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BlockingService::class.java))
        }
    }
}
