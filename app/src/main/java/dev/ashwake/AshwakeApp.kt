package dev.ashwake

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.ashwake.platform.notification.AshwakeNotifications
import dev.ashwake.platform.work.MilestoneWorker
import javax.inject.Inject

/**
 * Приложение офлайн-first: ни одного сетевого вызова, ни одного стороннего SDK.
 * WorkManager поднимается вручную через Hilt — штатный инициализатор выключен в манифесте.
 */
@HiltAndroidApp
class AshwakeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        AshwakeNotifications.createChannels(this)
        MilestoneWorker.schedule(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
