package dev.ashwake

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import dev.ashwake.platform.notification.AshwakeNotifications
import dev.ashwake.platform.work.BackupWorker
import dev.ashwake.platform.work.MilestoneWorker
import dev.ashwake.platform.work.RitualReminderWorker
import dev.ashwake.platform.work.WeeklyReportWorker
import dev.ashwake.platform.work.OnlineSyncWorker
import dev.ashwake.platform.work.CloudBackupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Приложение local-first: сеть используется только после настройки собственного API
 * и входа пользователя. WorkManager поднимается вручную через Hilt — штатный
 * инициализатор выключен в манифесте.
 */
@HiltAndroidApp
class AshwakeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    /**
     * Постановка периодических работ трогает базу WorkManager. Она нужна для
     * уведомлений и резервных копий, но не должна задерживать первый кадр UI.
     */
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        AshwakeNotifications.createChannels(this)

        // Robolectric не предоставляет настоящие системные receiver'ы,
        // нужные ограничениям WorkManager. Планирование периодических задач
        // в unit-тестах ничего не проверяет и оставляет фоновые корутины
        // между тестами; на устройстве эта ветка недостижима.
        if (Build.FINGERPRINT == "robolectric") return

        // WorkManagerInitializer выключен в манифесте ради HiltWorkerFactory.
        // Раньше инициализация с шестью enqueueUniquePeriodicWork выполнялась
        // до первого кадра Activity и давала заметный фриз на реальных
        // телефонах. Выполняем тот же обязательный набор в фоне.
        startupScope.launch {
            // Первую отрисовку отдаём UI без конкуренции с созданием базы
            // WorkManager. Уведомления и резервные копии от такой отсрочки
            // не теряют ни данных, ни точности.
            delay(750)
            if (!WorkManager.isInitialized()) {
                WorkManager.initialize(this@AshwakeApp, workManagerConfiguration)
            }
            MilestoneWorker.schedule(this@AshwakeApp)
            RitualReminderWorker.schedule(this@AshwakeApp)
            WeeklyReportWorker.schedule(this@AshwakeApp)
            BackupWorker.schedule(this@AshwakeApp)
            OnlineSyncWorker.schedule(this@AshwakeApp)
            CloudBackupWorker.schedule(this@AshwakeApp)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
