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
import dev.ashwake.domain.model.tasks.PostponeSource
import dev.ashwake.domain.model.tasks.TaskStatus
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.domain.scheduler.TaskReminderScheduler
import dev.ashwake.domain.usecase.tasks.CompleteTaskUseCase
import dev.ashwake.domain.usecase.tasks.PostponeTaskUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Срабатывание напоминания и кнопки прямо из шторки.
 *
 * Настойчивое напоминание (п. 1) реализовано цепочкой одиночных будильников:
 * каждое срабатывание ставит следующее через N минут и обрывается, как только
 * задача закрыта. Периодический alarm здесь не годится — его пришлось бы
 * отменять отдельно, и при промахе оставался бы вечный будильник.
 */
@AndroidEntryPoint
class TaskReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var scheduler: TaskReminderScheduler
    @Inject lateinit var completeTask: CompleteTaskUseCase
    @Inject lateinit var postponeTask: PostponeTaskUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, 0L)
        if (taskId == 0L) return
        val action = intent.action ?: return
        val pendingResult = goAsync()

        scope.launch {
            try {
                when (action) {
                    ACTION_FIRE -> fire(context, taskId)

                    ACTION_COMPLETE -> {
                        completeTask(taskId)
                        cancelNotification(context, taskId)
                    }

                    ACTION_POSTPONE -> {
                        postponeTask(taskId, source = PostponeSource.DIALOG)
                        cancelNotification(context, taskId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun fire(context: Context, taskId: Long) {
        val task = tasks.getTask(taskId) ?: return
        if (task.status != TaskStatus.ACTIVE) return

        showNotification(context, taskId, task.title, task.note)

        // Настойчивое напоминание: следующий звонок ставим только сейчас,
        // когда точно знаем, что задача всё ещё открыта.
        val repeatMinutes = task.persistentReminderMinutes
        if (repeatMinutes != null && repeatMinutes > 0) {
            scheduler.schedule(task)
        }
    }

    private fun showNotification(context: Context, taskId: Long, title: String, note: String?) {
        // На API 33+ без разрешения notify() молча ничего не сделает — выходим раньше,
        // чтобы не ставить следующее звено настойчивой цепочки впустую.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = PendingIntent.getActivity(
            context,
            taskId.toInt(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, AshwakeNotifications.CHANNEL_TASKS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .apply { note?.takeIf { it.isNotBlank() }?.let { setContentText(it) } }
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(
                0, "Выполнено",
                actionIntent(context, taskId, ACTION_COMPLETE, REQUEST_COMPLETE)
            )
            .addAction(
                0, "На завтра",
                actionIntent(context, taskId, ACTION_POSTPONE, REQUEST_POSTPONE)
            )
            .build()

        context.getSystemService<NotificationManager>()
            ?.notify(notificationId(taskId), notification)
    }

    private fun actionIntent(
        context: Context,
        taskId: Long,
        action: String,
        requestOffset: Int
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        taskId.toInt() * REQUEST_STRIDE + requestOffset,
        Intent(context, TaskReminderReceiver::class.java)
            .setAction(action)
            .putExtra(EXTRA_TASK_ID, taskId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun cancelNotification(context: Context, taskId: Long) {
        context.getSystemService<NotificationManager>()?.cancel(notificationId(taskId))
    }

    companion object {
        const val ACTION_FIRE = "dev.ashwake.action.TASK_REMINDER_FIRE"
        const val ACTION_COMPLETE = "dev.ashwake.action.TASK_COMPLETE"
        const val ACTION_POSTPONE = "dev.ashwake.action.TASK_POSTPONE"
        const val EXTRA_TASK_ID = "taskId"

        private const val REQUEST_STRIDE = 4
        private const val REQUEST_COMPLETE = 1
        private const val REQUEST_POSTPONE = 2

        fun notificationId(taskId: Long): Int = (taskId % Int.MAX_VALUE).toInt()
    }
}
