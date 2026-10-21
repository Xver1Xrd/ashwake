package dev.ashwake.ui.tasks.detail

import androidx.lifecycle.SavedStateHandle
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.model.tasks.TaskAttachment
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.domain.usecase.tasks.CompleteTaskUseCase
import dev.ashwake.domain.usecase.tasks.ReopenTaskUseCase
import dev.ashwake.data.attachments.TaskAttachmentStore
import dev.ashwake.core.time.AppClock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val attachmentStore: TaskAttachmentStore,
    private val completeTask: CompleteTaskUseCase,
    private val reopenTask: ReopenTaskUseCase,
    private val clock: AppClock,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val taskId = savedStateHandle.get<String>("taskId")?.toLongOrNull() ?: 0L
    val task: StateFlow<Task?> = tasks.observeTask(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _attachmentError = MutableStateFlow<String?>(null)
    val attachmentError = _attachmentError.asStateFlow()

    fun dismissAttachmentError() { _attachmentError.value = null }

    fun addAttachments(uris: List<Uri>) = viewModelScope.launch {
        for (uri in uris) {
            val saved = attachmentStore.import(uri)
            if (saved == null) {
                _attachmentError.value = "Не удалось добавить одно из вложений"
                continue
            }
            try {
                tasks.addAttachment(TaskAttachment(
                    taskId = taskId, fileName = saved.fileName, displayName = saved.displayName,
                    mimeType = saved.mimeType, sizeBytes = saved.sizeBytes, createdAt = clock.now()
                ))
            } catch (error: Exception) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                attachmentStore.delete(saved.fileName)
                _attachmentError.value = "Не удалось сохранить вложение"
            }
        }
    }

    fun toggleDone() = viewModelScope.launch {
        val current = task.value ?: return@launch
        if (current.isDone) reopenTask(current.id) else completeTask(current.id)
    }

    fun removeAttachment(attachment: TaskAttachment) = viewModelScope.launch {
        tasks.removeAttachment(attachment.id)
        attachmentStore.delete(attachment.fileName)
    }
}
