package dev.ashwake.domain.repository.tasks

import dev.ashwake.domain.model.tasks.AttachmentKind
import dev.ashwake.domain.model.tasks.TaskAttachment
import kotlinx.coroutines.flow.Flow

interface TaskAttachmentRepository {
    fun observe(taskId: Long): Flow<List<TaskAttachment>>
    suspend fun add(taskId: Long, sourceUri: String, kind: AttachmentKind)
    suspend fun delete(attachmentId: Long)
}
