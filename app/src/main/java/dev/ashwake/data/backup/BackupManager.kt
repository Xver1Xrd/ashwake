package dev.ashwake.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

sealed interface BackupResult {
    data class Success(val fileName: String, val contents: BackupContents) : BackupResult
    data object NoFolder : BackupResult
    data class Failed(val reason: String) : BackupResult
}

sealed interface RestoreResult {
    data class Preview(val contents: BackupContents) : RestoreResult
    data object NeedsPassword : RestoreResult
    data object WrongPassword : RestoreResult
    data class Failed(val reason: String) : RestoreResult

    /** Данные заменены. [contents] — что реально записано в базу. */
    data class Restored(val contents: BackupContents) : RestoreResult
}

/**
 * Резервные копии (п. 13).
 *
 * Файл кладётся в выбранную пользователем папку через SAF, чтобы её подхватывал
 * Syncthing или любая другая синхронизация: приложение офлайн-first и никуда
 * само ничего не отправляет — за перенос отвечает то, чему человек уже доверяет.
 *
 * Имя файла содержит дату, старые копии сверх [KEEP_COPIES] удаляются.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serializer: JsonBackupSerializer,
    private val crypto: ArchiveCrypto,
    private val settings: AppSettings,
    private val clock: AppClock
) {

    suspend fun createBackup(password: CharArray? = null): BackupResult =
        withContext(Dispatchers.IO) {
            val folderUri = settings.backupFolderUri.first()
                ?: return@withContext BackupResult.NoFolder
            val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUri))
                ?: return@withContext BackupResult.NoFolder
            if (!folder.canWrite()) {
                return@withContext BackupResult.Failed("Нет доступа к папке")
            }

            runCatching {
                val (json, contents) = serializer.export()
                val encrypted = password != null

                val bytes = if (encrypted) {
                    crypto.encrypt(json.toByteArray(), password)
                } else {
                    json.toByteArray()
                }

                val name = fileName(encrypted)
                val file = folder.createFile(
                    if (encrypted) MIME_BINARY else MIME_JSON,
                    name
                ) ?: return@runCatching BackupResult.Failed("Не удалось создать файл")

                context.contentResolver.openOutputStream(file.uri)?.use { it.write(bytes) }
                rotate(folder)
                BackupResult.Success(name, contents)
            }.getOrElse { error ->
                BackupResult.Failed(error.message ?: "Не удалось сохранить копию")
            }
        }

    /**
     * Чтение архива для предпросмотра.
     *
     * Восстановление необратимо, поэтому сначала показывается, что внутри,
     * и только потом предлагается заменить данные.
     */
    suspend fun readBackup(uri: Uri, password: CharArray? = null): RestoreResult =
        withContext(Dispatchers.IO) {
            decodeArchive(uri, password) { json ->
                val contents = serializer.peek(json)
                    ?: return@decodeArchive RestoreResult.Failed(
                        "Файл не похож на архив Ashwake"
                    )
                RestoreResult.Preview(contents)
            }
        }

    /**
     * Восстановление: полная замена данных содержимым архива.
     *
     * Вызывается только после того, как человек увидел предпросмотр и
     * подтвердил замену. Операция необратима — но именно она делает
     * резервную копию копией, а не файлом на память.
     */
    suspend fun restore(uri: Uri, password: CharArray? = null): RestoreResult =
        withContext(Dispatchers.IO) {
            decodeArchive(uri, password) { json ->
                if (serializer.peek(json) == null) {
                    return@decodeArchive RestoreResult.Failed("Файл не похож на архив Ashwake")
                }
                RestoreResult.Restored(serializer.import(json))
            }
        }

    /**
     * Общая часть чтения и восстановления: прочитать файл, при необходимости
     * расшифровать и отдать текст архива. Дублировать эти пять веток
     * в двух методах значило бы чинить ошибки в них по отдельности.
     */
    private suspend fun decodeArchive(
        uri: Uri,
        password: CharArray?,
        block: suspend (String) -> RestoreResult
    ): RestoreResult = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return@runCatching RestoreResult.Failed("Файл не читается")

        val json = when {
            !crypto.isEncrypted(bytes) -> String(bytes)
            password == null -> return@runCatching RestoreResult.NeedsPassword
            else -> when (val result = crypto.decrypt(bytes, password)) {
                is DecryptResult.Success -> String(result.content)
                DecryptResult.WrongPassword -> return@runCatching RestoreResult.WrongPassword
                is DecryptResult.Corrupted -> return@runCatching RestoreResult.Failed(result.reason)
                DecryptResult.NotEncrypted -> String(bytes)
            }
        }
        block(json)
    }.getOrElse { error ->
        RestoreResult.Failed(error.message ?: "Не удалось прочитать архив")
    }

    /** Ротация: держим последние [KEEP_COPIES] копий, остальное удаляем. */
    private fun rotate(folder: DocumentFile) {
        folder.listFiles()
            .filter { it.name?.startsWith(FILE_PREFIX) == true }
            .sortedByDescending { it.lastModified() }
            .drop(KEEP_COPIES)
            .forEach { it.delete() }
    }

    private fun fileName(encrypted: Boolean): String {
        val stamp = clock.now()
            .atZone(clock.zone() ?: ZoneId.systemDefault())
            .format(FILE_STAMP)
        return "$FILE_PREFIX$stamp" + if (encrypted) ".ashw" else ".json"
    }

    private companion object {
        const val FILE_PREFIX = "ashwake-"
        const val KEEP_COPIES = 7
        const val MIME_JSON = "application/json"
        const val MIME_BINARY = "application/octet-stream"

        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
    }
}
