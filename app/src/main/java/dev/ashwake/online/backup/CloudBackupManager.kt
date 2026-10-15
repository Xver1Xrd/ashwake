package dev.ashwake.online.backup

import dev.ashwake.data.backup.BackupManager
import dev.ashwake.data.backup.BackupContents
import dev.ashwake.data.backup.RestoreResult
import dev.ashwake.online.api.OnlineApi
import dev.ashwake.online.crypto.RecoveryManager
import dev.ashwake.online.repository.OnlineRepository
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

data class CloudRestorePreview(
    val backupId: String,
    val createdAt: String,
    val contents: BackupContents,
    val json: String
)

@Singleton
class CloudBackupManager @Inject constructor(
    private val backups: BackupManager,
    private val recovery: RecoveryManager,
    private val online: OnlineRepository,
    private val api: OnlineApi
) {
    suspend fun upload(): String {
        val code = recovery.storedCode() ?: error("Сначала создайте код восстановления")
        val (payload, contents) = backups.createEncryptedPayload(code.toCharArray())
        val hash = MessageDigest.getInstance("SHA-256").digest(payload)
            .joinToString("") { "%02x".format(it) }
        val token = online.token()
        val request = api.createBackup(payload.size.toLong(), hash, token)
        api.uploadBackup(request, payload)
        api.completeBackup(request.id, token)
        return "Сохранено: ${contents.tasks} задач, ${contents.habits} привычек"
    }

    suspend fun previewLatest(): CloudRestorePreview {
        val code = recovery.storedCode() ?: error("На устройстве нет кода восстановления")
        val token = online.token()
        val latest = api.backups(token).firstOrNull() ?: error("Облачных копий пока нет")
        val payload = api.downloadBackup(latest.id, token)
        val actualHash = MessageDigest.getInstance("SHA-256").digest(payload)
            .joinToString("") { "%02x".format(it) }
        check(actualHash == latest.sha256) { "Контрольная сумма облачной копии не совпала" }
        return when (val result = backups.readPayload(payload, code.toCharArray())) {
            is RestoreResult.Preview -> CloudRestorePreview(
                backupId = latest.id,
                createdAt = latest.createdAt,
                contents = result.contents,
                json = result.json
            )
            RestoreResult.WrongPassword -> error("Код восстановления не подходит к копии")
            RestoreResult.NeedsPassword -> error("Копия требует код восстановления")
            is RestoreResult.Failed -> error(result.reason)
        }
    }

    suspend fun restore(preview: CloudRestorePreview): String {
        return when (val result = backups.restore(preview.json)) {
            is RestoreResult.Preview ->
                "Восстановлено: ${result.contents.tasks} задач, ${result.contents.habits} привычек"
            RestoreResult.WrongPassword -> error("Код восстановления не подходит")
            RestoreResult.NeedsPassword -> error("Нужен код восстановления")
            is RestoreResult.Failed -> error(result.reason)
        }
    }
}
