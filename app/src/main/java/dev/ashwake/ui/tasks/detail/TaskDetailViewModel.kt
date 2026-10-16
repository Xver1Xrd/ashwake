package dev.ashwake.ui.tasks.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.model.tasks.TaskAttachment
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.data.attachments.TaskAttachmentStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val attachmentStore: TaskAttachmentStore,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val taskId = savedStateHandle.get<String>("taskId")?.toLongOrNull() ?: 0L
    val task: StateFlow<Task?> = tasks.observeTask(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleDone() = viewModelScope.launch {
        val current = task.value ?: return@launch
        if (current.isDone) tasks.reopen(current.id) else tasks.complete(current.id)
    }

    fun removeAttachment(attachment: TaskAttachment) = viewModelScope.launch {
        tasks.removeAttachment(attachment.id)
        attachmentStore.delete(attachment.fileName)
    }
}
