package dev.ashwake.platform.blocking

import android.os.Build
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.provider.Settings
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Определение приложения на переднем плане через `UsageStatsManager` (п. 7).
 *
 * Выбран этот путь, а не `AccessibilityService`: он честнее по разрешениям
 * и не рискует модерацией магазина. Плата — задержка около секунды
 * и опрос вместо события, поэтому окно запрашивается с запасом:
 * система отдаёт события пачками и слишком узкий интервал бывает пустым.
 */
@Singleton
class ForegroundAppMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService<AppOpsManager>() ?: return false
        // `unsafeCheckOpNoThrow` появился в API 29, до него то же самое
        // называлось `checkOpNoThrow`. Вызов без развилки компилируется молча
        // и падает NoSuchMethodError на Android 8 и 9 — там, где блокировка
        // приложений как раз нужнее всего
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    /** @return пакет приложения на переднем плане или null, если определить не вышло. */
    fun currentForegroundPackage(): String? {
        if (!hasUsageAccess()) return null
        val usage = context.getSystemService<UsageStatsManager>() ?: return null

        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - QUERY_WINDOW_MILLIS, now)
        val event = android.app.usage.UsageEvents.Event()

        var lastPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastPackage = event.packageName
            }
        }
        return lastPackage
    }

    private companion object {
        /** Десять секунд: события приходят пачками, узкое окно бывает пустым. */
        const val QUERY_WINDOW_MILLIS = 10_000L
    }
}
