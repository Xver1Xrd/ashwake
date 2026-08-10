package dev.ashwake.platform.tile

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.TileService
import dev.ashwake.platform.widget.AppRoutes
import dev.ashwake.platform.widget.appIntent

/**
 * Плитки быстрых настроек (п. 11).
 *
 * На API 34+ система требует запускать активность через
 * `startActivityAndCollapse(PendingIntent)`; на более старых работает
 * версия с интентом. Обе ветки нужны, потому что minSdk 26.
 */
abstract class RouteTileService(private val route: String) : TileService() {

    override fun onClick() {
        super.onClick()
        val intent = appIntent(this, route)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, route.hashCode(), intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

/** «Добавить задачу» из шторки быстрых настроек. */
class AddTaskTileService : RouteTileService(AppRoutes.NEW_TASK)

/** «Помодоро» из шторки быстрых настроек. */
class PomodoroTileService : RouteTileService(AppRoutes.FOCUS_START)
