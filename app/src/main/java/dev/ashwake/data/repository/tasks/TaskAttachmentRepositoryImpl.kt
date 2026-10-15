package dev.ashwake.data.repository.tasks

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.core.time.AppClock
import dev.ashwake.data.db.dao.tasks.TaskAttachmentDao
import dev.ashwake.data.db.entity.tasks.TaskAttachmentEntity
import dev.ashwake.domain.model.tasks.AttachmentKind
import dev.ashwake.domain.model.tasks.TaskAttachment
import dev.ashwake.domain.repository.tasks.TaskAttachmentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskAttachmentStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val directory: File get() = File(context.filesDir, DIRECTORY).apply { mkdirs() }

    fun file(storedName: String): File {
        require(STORED_NAME.matches(storedName)) { "Некорректное имя вложения" }
        return File(directory, storedName)
    }

    fun delete(storedName: String) {
        runCatching { file(storedName).delete() }
    }

    fun contentUri(storedName: String): String = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file(storedName)
    ).toString()

    fun copyFrom(source: Uri): StoredAttachment {
        val resolver = context.contentResolver
        val metadata = resolver.query(
            source,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
        val displayName = sanitizeName(metadata ?: "Вложение")
        val storedName = UUID.randomUUID().toString()
        val target = file(storedName)
        val temporary = File(directory, ".$storedName.tmp")

        try {
            val input = resolver.openInputStream(source) ?: error("Не удалось открыть файл")
            val size = input.use { sourceStream ->
                temporary.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = sourceStream.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_ATTACHMENT_BYTES) {
                            "Файл больше 25 МБ"
                        }
                        output.write(buffer, 0, read)
                    }
                    total
                }
            }
            check(temporary.renameTo(target)) { "Не удалось сохранить файл" }
            return StoredAttachment(
                storedName = storedName,
                displayName = displayName,
                mimeType = resolver.getType(source)?.take(120) ?: "application/octet-stream",
                sizeBytes = size
            )
        } catch (error: Throwable) {
            temporary.delete()
            target.delete()
            throw error
        }
    }

    private fun sanitizeName(value: String): String = value
        .replace(Regex("[\\p{Cntrl}/\\\\]+"), "_")
        .trim()
        .take(MAX_DISPLAY_NAME)
        .ifBlank { "Вложение" }

    data class StoredAttachment(
        val storedName: String,
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long
    )

    companion object {
        const val DIRECTORY = "task_attachments"
        const val MAX_ATTACHMENT_BYTES = 25L * 1024 * 1024
        const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
        const val MAX_ATTACHMENTS_PER_TASK = 20
        private const val MAX_DISPLAY_NAME = 180
        val STORED_NAME = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}

@Singleton
class TaskAttachmentRepositoryImpl @Inject constructor(
    private val dao: TaskAttachmentDao,
    private val store: TaskAttachmentStore,
    private val clock: AppClock
) : TaskAttachmentRepository {
    override fun observe(taskId: Long): Flow<List<TaskAttachment>> =
        dao.observeAttachments(taskId).map { rows -> rows.map(::toDomain) }

    override suspend fun add(taskId: Long, sourceUri: String, kind: AttachmentKind) {
        withContext(Dispatchers.IO) {
            require(dao.countForTask(taskId) < TaskAttachmentStore.MAX_ATTACHMENTS_PER_TASK) {
                "К одной задаче можно прикрепить не больше 20 файлов"
            }
            val copied = store.copyFrom(Uri.parse(sourceUri))
            try {
                require(dao.totalSizeBytes() + copied.sizeBytes <= TaskAttachmentStore.MAX_TOTAL_BYTES) {
                    "Общий объём вложений не может превышать 64 МБ"
                }
                dao.insertAttachment(
                    TaskAttachmentEntity(
                        taskId = taskId,
                        storedName = copied.storedName,
                        displayName = copied.displayName,
                        mimeType = copied.mimeType,
                        sizeBytes = copied.sizeBytes,
                        kind = kind.name,
                        createdAt = clock.now().toEpochMilli()
                    )
                )
            } catch (error: Throwable) {
                store.delete(copied.storedName)
                throw error
            }
        }
    }

    override suspend fun delete(attachmentId: Long) {
        withContext(Dispatchers.IO) {
            val attachment = dao.attachment(attachmentId) ?: return@withContext
            dao.deleteAttachment(attachmentId)
            store.delete(attachment.storedName)
        }
    }

    private fun toDomain(entity: TaskAttachmentEntity) = TaskAttachment(
        id = entity.id,
        taskId = entity.taskId,
        displayName = entity.displayName,
        mimeType = entity.mimeType,
        sizeBytes = entity.sizeBytes,
        kind = runCatching { AttachmentKind.valueOf(entity.kind) }.getOrDefault(AttachmentKind.FILE),
        contentUri = store.contentUri(entity.storedName)
    )
}
