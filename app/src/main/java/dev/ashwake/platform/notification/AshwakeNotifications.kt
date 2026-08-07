package dev.ashwake.platform.notification

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
                CHANNEL_TASKS, "Задачи", NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Напоминания о задачах, в том числе настойчивые" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_HABITS, "Привычки", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Напоминания об отметке привычек" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ABSTINENCE, "Отказы", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Вехи и поддержка при тяге" }
        )
        // Низкий приоритет: липкие счётчики и foreground-сервисы не должны звенеть
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING, "Постоянные счётчики", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Счётчики отказов, таймеры фокуса и рутин" }
        )
    }
}
