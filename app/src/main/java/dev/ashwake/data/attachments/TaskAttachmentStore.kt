package dev.ashwake.data.attachments

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Копирует вложения в приватное хранилище: URI галереи не переживает перезапуск. */
@Singleton
class TaskAttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val directory: File get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    suspend fun import(uri: Uri): SavedAttachment? = withContext(Dispatchers.IO) {
        var output: File? = null
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri) ?: "application/octet-stream"
            val displayName = resolver.query(uri, null, null, null, null)?.use { cursor ->
                val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && column >= 0) cursor.getString(column) else null
            } ?: "image"
            val extension = displayName.substringAfterLast('.', "").takeIf { it.isNotBlank() }
            val fileName = UUID.randomUUID().toString() + extension?.let { ".$it" }.orEmpty()
            val target = File(directory, fileName)
            output = target
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { stream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= MAX_ATTACHMENT_BYTES) { "Файл больше 25 МБ" }
                        stream.write(buffer, 0, count)
                    }
                }
            } ?: return@runCatching null
            SavedAttachment(fileName, displayName, mimeType, target.length())
        }.onFailure { output?.delete() }.getOrNull()
    }

    fun file(name: String): File = File(directory, name)
    fun delete(name: String) { runCatching { file(name).delete() } }

    data class SavedAttachment(val fileName: String, val displayName: String, val mimeType: String, val sizeBytes: Long)

    companion object {
        const val DIRECTORY = "task_attachments"
        const val MAX_ATTACHMENT_BYTES = 25L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
        val STORED_NAME = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(?:\\.[A-Za-z0-9]{1,12})?")
    }
}
