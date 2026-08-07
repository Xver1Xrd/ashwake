package dev.ashwake.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.ashwake.domain.scheduler.HabitReminderScheduler
import dev.ashwake.domain.scheduler.TaskReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Будильники не переживают перезагрузку — расставляем их заново. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var taskScheduler: TaskReminderScheduler
    @Inject lateinit var habitScheduler: HabitReminderScheduler

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                taskScheduler.rescheduleAll()
                habitScheduler.rescheduleAll()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
