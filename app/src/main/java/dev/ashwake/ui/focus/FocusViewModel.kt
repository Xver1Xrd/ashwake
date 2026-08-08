package dev.ashwake.ui.focus

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.domain.model.focus.FocusMode
import dev.ashwake.domain.model.focus.FocusStats
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.repository.routines.FocusRepository
import dev.ashwake.domain.repository.tasks.TaskFilter
import dev.ashwake.domain.repository.tasks.TaskRepository
import dev.ashwake.platform.service.SessionForegroundService
import dev.ashwake.platform.session.FocusController
import dev.ashwake.platform.session.FocusRunState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class FocusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val controller: FocusController,
    focusRepository: FocusRepository,
    taskRepository: TaskRepository
) : ViewModel() {

    val run: StateFlow<FocusRunState> = controller.state
    val config = controller.config

    val stats: StateFlow<FocusStats> = focusRepository.observeStats()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            FocusStats(0, 0, emptyMap(), emptyMap(), emptyMap())
        )

    /** Активные задачи для привязки сессии (п. 8). */
    val tasks: StateFlow<List<Task>> = taskRepository.observeTasks(TaskFilter())
        .map { list -> list.filterNot { it.isDone } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var selectedTaskId: Long? by mutableStateOf(null)
        private set

    fun selectTask(id: Long?) { selectedTaskId = id }

    fun start(mode: FocusMode) {
        val task = tasks.value.firstOrNull { it.id == selectedTaskId }
        controller.start(
            mode = mode,
            taskId = task?.id,
            taskTitle = task?.title,
            projectId = task?.projectId
        )
        // Таймер обязан идти при закрытом экране — поднимаем foreground-сервис
        SessionForegroundService.start(context)
    }

    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun skipPhase() = controller.skipPhase()

    fun stop() {
        controller.stop()
        SessionForegroundService.stop(context)
    }

    fun setWorkMinutes(minutes: Int) {
        controller.setConfig(controller.config.value.copy(workMinutes = minutes))
    }
}
