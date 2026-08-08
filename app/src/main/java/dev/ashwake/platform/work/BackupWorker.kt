package dev.ashwake.platform.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.ashwake.data.backup.BackupManager
import dev.ashwake.data.backup.BackupResult
import dev.ashwake.data.settings.AppSettings
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Авто-бэкап раз в сутки (п. 13).
 *
 * Пароль нигде не хранится, поэтому автоматический бэкап всегда незашифрованный —
 * зашифровать без пароля нечем. Пользователь либо кладёт папку туда, где
 * шифрование обеспечивает синхронизация, либо делает зашифрованную копию
 * вручную, вводя пароль. Врать про «автоматическое шифрование» нельзя.
 */
@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val backups: BackupManager,
    private val settings: AppSettings
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (settings.backupFolderUri.first() == null) return Result.success()

        return when (val result = backups.createBackup(password = null)) {
            is BackupResult.Success -> Result.success()
            BackupResult.NoFolder -> Result.success()
            // Папку могли отозвать или переполнить — пробуем ещё раз позже
            is BackupResult.Failed -> Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "daily-backup"

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
                    .setConstraints(
                        // Сеть не нужна: приложение офлайн-first, файл пишется локально
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .build()
            )
        }
    }
}
