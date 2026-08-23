package dev.ashwake.platform.notification

import dev.ashwake.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.content.getSystemService

/**
 * Каналы уведомлений. Заводятся один раз при старте приложения.
 *
 * Каналов несколько намеренно: пользователь должен уметь приглушить
 * напоминания по задачам, не трогая счётчики отказов и наоборот.
 */
object AshwakeNotifications {

    const val CHANNEL_TASKS = "tasks"
    const val CHANNEL_HABITS = "habits"
    const val CHANNEL_ABSTINENCE = "abstinence"
    const val CHANNEL_ONGOING = "ongoing"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_TASKS, context.getString(R.string.tasks_zadachi), NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.ashwakenotificat_napominaniya_o_zadachah_v_tom_chisle_nastoyc) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_HABITS, context.getString(R.string.habits_privychki), NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.ashwakenotificat_napominaniya_ob_otmetke_privychek) }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ABSTINENCE, context.getString(R.string.abstinence_otkazy), NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.ashwakenotificat_vehi_i_podderzhka_pri_tyage) }
        )
        // Низкий приоритет: липкие счётчики и foreground-сервисы не должны звенеть
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING, context.getString(R.string.ashwakenotificat_postoyannye_schetchiki), NotificationManager.IMPORTANCE_LOW
            ).apply { description = context.getString(R.string.ashwakenotificat_schetchiki_otkazov_taymery_fokusa_i_rutin) }
        )
    }
}
