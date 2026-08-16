package dev.ashwake.ui.routines

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.ashwake.domain.model.routines.Routine
import dev.ashwake.domain.model.routines.RoutineStep
import dev.ashwake.domain.repository.routines.RoutineRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Редактор рутины: название и шаги.
 *
 * У новой рутины routineId = 0, у существующей — из маршрута. При сохранении
 * у существующей сохраняются настройки (время, будильник, TTS), меняются
 * только название и шаги.
 */
@HiltViewModel
class RoutineEditorViewModel @Inject constructor(
    private val routines: RoutineRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val routineId: Long =
        savedStateHandle.get<String>("routineId")?.toLongOrNull() ?: 0L

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name.asStateFlow()

    private val _steps = MutableStateFlow<List<RoutineStep>>(emptyList())
    val steps: StateFlow<List<RoutineStep>> = _steps.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    init {
        if (routineId > 0L) {
            viewModelScope.launch {
                routines.getRoutine(routineId)?.let { routine ->
                    _name.value = routine.name
                    _steps.value = routine.steps.sortedBy { it.position }
                }
                _loaded.value = true
            }
        } else {
            _loaded.value = true
        }
    }

    fun setName(value: String) { _name.value = value }

    fun addStep(title: String, minutes: Int) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        _steps.value = _steps.value + RoutineStep(
            title = trimmed,
            durationSeconds = minutes.coerceAtLeast(1) * 60
        )
    }

    fun updateStepTitle(index: Int, title: String) {
        if (index !in _steps.value.indices) return
        _steps.value = _steps.value.toMutableList().also { it[index] = it[index].copy(title = title) }
    }

    fun updateStepDuration(index: Int, minutes: Int) {
        if (index !in _steps.value.indices) return
        _steps.value = _steps.value.toMutableList().also {
            it[index] = it[index].copy(durationSeconds = minutes.coerceAtLeast(1) * 60)
        }
    }

    fun removeStep(index: Int) {
        if (index !in _steps.value.indices) return
        _steps.value = _steps.value.toMutableList().also { it.removeAt(index) }
    }

    /** Сдвиг шага на +1/-1 по списку. */
    fun moveStep(index: Int, delta: Int) {
        val target = index + delta
        if (index !in _steps.value.indices || target !in _steps.value.indices) return
        val list = _steps.value.toMutableList()
        val step = list.removeAt(index)
        list.add(target, step)
        _steps.value = list
    }

    fun consumeMessage() { _message.value = null }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            val trimmed = _name.value.trim()
            if (trimmed.isEmpty()) {
                _message.value = "Назовите рутину"
                return@launch
            }
            if (_steps.value.isEmpty()) {
                _message.value = "Добавьте хотя бы один шаг"
                return@launch
            }
            val existing = if (routineId > 0L) routines.getRoutine(routineId) else null
            val saved: Routine = (existing ?: Routine(name = trimmed)).copy(
                name = trimmed,
                steps = _steps.value.mapIndexed { index, step -> step.copy(position = index) }
            )
            routines.upsertRoutine(saved)
            onDone()
        }
    }
}
