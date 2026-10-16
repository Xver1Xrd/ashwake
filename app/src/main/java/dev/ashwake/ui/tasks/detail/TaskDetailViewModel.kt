package dev.ashwake.ui.tasks.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.repository.tasks.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    tasks: TaskRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val taskId = savedStateHandle.get<String>("taskId")?.toLongOrNull() ?: 0L
    val task: StateFlow<Task?> = tasks.observeTask(taskId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
