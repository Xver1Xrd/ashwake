package dev.ashwake.ui.routines

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.ashwake.data.assets.RoutinePreset
import dev.ashwake.data.assets.RoutinePresetLoader
import dev.ashwake.domain.engine.routines.RoutineProgressCalculator
import dev.ashwake.domain.engine.routines.SessionSummary
import dev.ashwake.domain.model.routines.Routine
import dev.ashwake.domain.model.routines.RoutineStep
import dev.ashwake.domain.repository.routines.RoutineRepository
import dev.ashwake.platform.service.SessionForegroundService
import dev.ashwake.platform.session.RoutineRunController
import dev.ashwake.platform.session.RoutineRunState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutinesViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val routines: RoutineRepository,
    private val controller: RoutineRunController,
    private val presetLoader: RoutinePresetLoader,
    private val calculator: RoutineProgressCalculator
) : ViewModel() {

    val list: StateFlow<List<Routine>> = routines.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val run: StateFlow<RoutineRunState> = controller.state

    private val _presets = MutableStateFlow<List<RoutinePreset>>(emptyList())
    val presets: StateFlow<List<RoutinePreset>> = _presets.asStateFlow()

    /** Итог последней завершённой сессии — экран «план vs факт» (п. 6). */
    private val _summary = MutableStateFlow<SessionSummary?>(null)
    val summary: StateFlow<SessionSummary?> = _summary.asStateFlow()

    init {
        viewModelScope.launch { _presets.value = presetLoader.load() }
    }

    fun start(routine: Routine) {
        if (routine.steps.isEmpty()) return
        controller.start(routine.id)
        // Сервис поднимается вместе с прогоном: таймер обязан идти при закрытом экране
        SessionForegroundService.start(context)
    }

    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun skip() = controller.skipStep()
    fun next() = controller.nextStep()
    fun adjust(deltaMinutes: Int) = controller.adjustCurrentStep(deltaMinutes)
    fun addStep(title: String, minutes: Int) = controller.addStep(title, minutes * 60)

    fun finish() {
        val sessionId = controller.state.value.sessionId
        controller.finish(completed = false)
        loadSummary(sessionId)
    }

    /** Прогон закончился сам — показываем итог и гасим сервис. */
    fun onRunFinished() {
        val sessionId = controller.state.value.sessionId
        loadSummary(sessionId)
        SessionForegroundService.stop(context)
    }

    private fun loadSummary(sessionId: Long) {
        if (sessionId == 0L) return
        viewModelScope.launch {
            routines.getSession(sessionId)?.let { _summary.value = calculator.summarize(it) }
        }
    }

    fun dismissSummary() {
        _summary.value = null
        controller.dismiss()
        SessionForegroundService.stop(context)
    }

    // --- редактирование ----------------------------------------------------

    fun createFromPreset(preset: RoutinePreset) {
        viewModelScope.launch {
            routines.upsertRoutine(
                Routine(
                    name = preset.name,
                    steps = preset.steps.mapIndexed { index, step ->
                        RoutineStep(
                            title = step.first,
                            durationSeconds = step.second,
                            position = index
                        )
                    }
                )
            )
        }
    }

    fun archive(routine: Routine) {
        viewModelScope.launch { routines.archiveRoutine(routine.id) }
    }
}
