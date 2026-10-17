package dev.ashwake.platform.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.ashwake.online.backup.CloudBackupManager
import dev.ashwake.online.repository.OnlineRepository
import java.util.concurrent.TimeUnit

@HiltWorker
class CloudBackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val online: OnlineRepository,
    private val backups: CloudBackupManager
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!online.configured || !online.hasSession()) return Result.success()
        return runCatching { backups.upload() }.fold(
            onSuccess = { Result.success() },
            onFailure = { if (runAttemptCount < 5) Result.retry() else Result.failure() }
        )
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "encrypted-cloud-backup",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CloudBackupWorker>(1, TimeUnit.DAYS)
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .setRequiresBatteryNotLow(true)
                            .build()
                    )
                    .build()
            )
        }
    }
}
