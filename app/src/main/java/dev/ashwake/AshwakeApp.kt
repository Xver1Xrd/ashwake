package dev.ashwake

import android.app.Application
import android.os.StrictMode
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
import javax.inject.Inject

/**
 * Приложение local-first: сеть используется только после настройки собственного API
 * и входа пользователя. WorkManager поднимается вручную через Hilt — штатный
 * инициализатор выключен в манифесте.
 */
@HiltAndroidApp
class AshwakeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        enableStrictModeInDebug()
        super.onCreate()
        // WorkManagerInitializer выключен в манифесте, чтобы использовать
        // HiltWorkerFactory. Инициализируем его до постановки любых задач:
        // на части устройств ленивый путь ещё не успевает получить
        // Configuration.Provider и приложение падает на первом getInstance().
        if (!WorkManager.isInitialized()) {
            WorkManager.initialize(this, workManagerConfiguration)
        }
        AshwakeNotifications.createChannels(this)
        MilestoneWorker.schedule(this)
        RitualReminderWorker.schedule(this)
        WeeklyReportWorker.schedule(this)
        BackupWorker.schedule(this)
        OnlineSyncWorker.schedule(this)
        CloudBackupWorker.schedule(this)
    }

    /**
     * StrictMode в debug-сборке (п. 17 приёмки).
     *
     * Ловит обращения к диску и базе в главном потоке — то, что на быстром
     * телефоне разработчика незаметно, а на медленном превращается в
     * подвисания списка. Только логирование: падать на этом в debug значило бы
     * ронять приложение из-за чужих библиотек, которые чинить всё равно нельзя.
     */
    private fun enableStrictModeInDebug() {
        if (!BuildConfig.DEBUG) return

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
