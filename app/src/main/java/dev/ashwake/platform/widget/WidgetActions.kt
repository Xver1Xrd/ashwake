package dev.ashwake.platform.widget

import android.content.Context
import android.content.Intent
import androidx.glance.action.ActionParameters
import dev.ashwake.MainActivity

/** Куда открыть приложение по тапу из виджета, тайла или шортката. */
object AppRoutes {
    const val EXTRA_ROUTE = "dev.ashwake.extra.ROUTE"

    const val TASKS = "tasks"
    const val NEW_TASK = "new-task"
    const val HABITS = "habits"
    const val ABSTINENCE = "abstinence"
    const val TIMERS = "timers"
    const val FOCUS_START = "focus-start"
    const val ROUTINE_START = "routine-start"
    const val RITUAL = "ritual"
}

/**
 * Интент запуска приложения на нужном экране.
 *
 * `CLEAR_TOP` и `SINGLE_TOP` вместе с `launchMode="singleTask"` в манифесте
 * дают одну и ту же копию приложения: без этого каждый тап по виджету
 * плодил бы новую задачу в списке недавних.
 */
fun appIntent(context: Context, route: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        putExtra(AppRoutes.EXTRA_ROUTE, route)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

/** Параметры действий виджетов. */
val TaskIdKey = ActionParameters.Key<Long>("taskId")
val HabitIdKey = ActionParameters.Key<Long>("habitId")
