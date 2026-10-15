package dev.ashwake.domain.model.tasks

enum class AttachmentKind { PHOTO, FILE }

data class TaskAttachment(
    val id: Long,
    val taskId: Long,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val kind: AttachmentKind,
    /** Одноразово выдаваемый FileProvider URI; прямой путь наружу не раскрывается. */
    val contentUri: String
)
