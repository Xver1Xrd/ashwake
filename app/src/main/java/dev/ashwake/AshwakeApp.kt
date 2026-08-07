package dev.ashwake

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Приложение офлайн-first: ни одного сетевого вызова, ни одного стороннего SDK.
 * WorkManager поднимается вручную через Hilt — штатный инициализатор выключен в манифесте.
 */
@HiltAndroidApp
class AshwakeApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
