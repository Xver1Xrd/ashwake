package dev.ashwake.platform.session

import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.engine.reward.RewardSource
import dev.ashwake.domain.engine.routines.RoutineProgressCalculator
import dev.ashwake.domain.engine.routines.RunProgress
import dev.ashwake.domain.model.routines.Routine
import dev.ashwake.domain.model.routines.RoutineSessionStep
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.routines.RoutineRepository
import dev.ashwake.domain.usecase.habits.FireAnchorsUseCase
import dev.ashwake.platform.tts.StepSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class RoutineRunState(
    val routine: Routine? = null,
    val sessionId: Long = 0,
    val steps: List<RoutineSessionStep> = emptyList(),
    val stepIndex: Int = 0,
    val stepElapsedSeconds: Int = 0,
    val running: Boolean = false,
    val finished: Boolean = false,
    val progress: RunProgress = RunProgress(0, 0, 0, 0, 0, 0f, 0f)
) {
    val active: Boolean get() = routine != null && !finished
    val currentStep: RoutineSessionStep? get() = steps.getOrNull(stepIndex)
    val nextStep: RoutineSessionStep? get() = steps.getOrNull(stepIndex + 1)
}

/**
 * Прогон рутины.
 *
 * Состояние живёт в синглтоне, а не в ViewModel: рутина продолжается,
 * когда экран закрыт, и её же показывает уведомление foreground-сервиса.
 * ViewModel только подписывается.
 *
 * Тик раз в секунду, а не пересчёт по системному времени: рутина — это
 * последовательность шагов с паузами и правками на ходу, и «сколько прошло
 * с момента старта» тут не совпадает с суммой шагов.
 */
@Singleton
class RoutineRunController @Inject constructor(
    private val routines: RoutineRepository,
    private val character: CharacterRepository,
    private val fireAnchors: FireAnchorsUseCase,
    private val calculator: RoutineProgressCalculator,
    private val speaker: StepSpeaker,
    private val clock: AppClock
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    private val _state = MutableStateFlow(RoutineRunState())
    val state: StateFlow<RoutineRunState> = _state.asStateFlow()

    fun start(routineId: Long) {
        scope.launch {
            val routine = routines.getRoutine(routineId) ?: return@launch
            if (routine.steps.isEmpty()) return@launch

            val sessionId = routines.startSession(routineId)
            val steps = routines.getSession(sessionId)?.steps.orEmpty()

            _state.value = RoutineRunState(
                routine = routine,
                sessionId = sessionId,
                steps = steps,
                running = true
            )
            speaker.prepare()
            announceCurrent()
            recompute()
            startTicker()
        }
    }

    fun pause() {
        ticker?.cancel()
        ticker = null
        _state.update { it.copy(running = false) }
    }

    fun resume() {
        if (_state.value.routine == null || _state.value.finished) return
        _state.update { it.copy(running = true) }
        startTicker()
    }

    /** Пропуск шага: он попадает в историю как пропущенный и рвёт «безупречность». */
    fun skipStep() {
        scope.launch { advance(skipped = true) }
    }

    fun nextStep() {
        scope.launch { advance(skipped = false) }
    }

    /** ±1 и ±10 минут на ходу (п. 6). */
    fun adjustCurrentStep(deltaMinutes: Int) {
        _state.update { current ->
            val step = current.currentStep ?: return@update current
            val updated = step.copy(
                plannedSeconds = calculator.adjustDuration(step.plannedSeconds, deltaMinutes)
            )
            current.copy(
                steps = current.steps.map { if (it.id == step.id) updated else it }
            ).also { scope.launch { routines.updateSessionStep(updated) } }
        }
        recompute()
    }

    fun addStep(title: String, durationSeconds: Int) {
        val sessionId = _state.value.sessionId
        if (sessionId == 0L || title.isBlank()) return
        scope.launch {
            routines.addSessionStep(sessionId, title.trim(), durationSeconds)
            val refreshed = routines.getSession(sessionId)?.steps.orEmpty()
            _state.update { it.copy(steps = refreshed) }
            recompute()
        }
    }

    /** Завершение — по последнему шагу или по кнопке «закончить». */
    fun finish(completed: Boolean = false) {
        val current = _state.value
        ticker?.cancel()
        ticker = null
        speaker.stop()

        scope.launch {
            if (current.sessionId != 0L) {
                persistCurrentStep(skipped = false)
                routines.finishSession(current.sessionId, completed, clock.now())

                if (completed) {
                    // Якорь «после рутины»: утренняя рутина закончилась —
                    // напоминаем о привычке, которая на неё завязана
                    current.routine?.id?.let { fireAnchors.onRoutineDone(it) }

                    val session = routines.getSession(current.sessionId)
                    character.grantReward(
                        RewardContext(
                            source = RewardSource.ROUTINE_DONE,
                            flawless = session?.flawless == true,
                            time = clock.now().atZone(clock.zone()).toLocalTime()
                        ),
                        refId = current.routine?.id?.toString()
                    )
                    character.grantStatPoints(
                        StatSource.ROUTINE_DONE,
                        refId = current.routine?.id?.toString()
                    )
                }
            }
            _state.update { it.copy(running = false, finished = true) }
        }
    }

    fun dismiss() {
        _state.value = RoutineRunState()
    }

    // --- внутреннее ---------------------------------------------------------

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                delay(1_000)
                val current = _state.value
                if (!current.running || current.finished) break

                val step = current.currentStep ?: break
                val elapsed = current.stepElapsedSeconds + 1
                _state.update { it.copy(stepElapsedSeconds = elapsed) }
                recompute()

                if (elapsed >= step.plannedSeconds) {
                    advance(skipped = false)
                }
            }
        }
    }

    /** Переход к следующему шагу с записью факта по текущему. */
    private suspend fun advance(skipped: Boolean) {
        persistCurrentStep(skipped)

        val current = _state.value
        val nextIndex = current.stepIndex + 1
        if (nextIndex >= current.steps.size) {
            finish(completed = true)
            return
        }

        _state.update { it.copy(stepIndex = nextIndex, stepElapsedSeconds = 0) }
        recompute()

        val routine = current.routine
        if (routine?.vibrateOnStep == true) speaker.vibrate()
        announceCurrent()
        if (current.running) startTicker()
    }

    private suspend fun persistCurrentStep(skipped: Boolean) {
        val current = _state.value
        val step = current.currentStep ?: return
        val updated = step.copy(
            actualSeconds = current.stepElapsedSeconds,
            skipped = skipped
        )
        routines.updateSessionStep(updated)
        _state.update { state ->
            state.copy(steps = state.steps.map { if (it.id == step.id) updated else it })
        }
    }

    private fun announceCurrent() {
        val current = _state.value
        if (current.routine?.ttsEnabled != true) return
        current.currentStep?.let { speaker.speak(it.title) }
    }

    private fun recompute() {
        _state.update { current ->
            current.copy(
                progress = calculator.progress(
                    steps = current.steps,
                    stepIndex = current.stepIndex,
                    stepElapsedSeconds = current.stepElapsedSeconds
                )
            )
        }
    }
}
