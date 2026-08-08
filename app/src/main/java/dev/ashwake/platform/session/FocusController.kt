package dev.ashwake.platform.session

import dev.ashwake.core.time.AppClock
import dev.ashwake.domain.engine.character.StatSource
import dev.ashwake.domain.engine.focus.PomodoroPlanner
import dev.ashwake.domain.engine.reward.RewardContext
import dev.ashwake.domain.engine.reward.RewardSource
import dev.ashwake.domain.model.focus.FocusMode
import dev.ashwake.domain.model.focus.FocusPhase
import dev.ashwake.domain.model.focus.PomodoroConfig
import dev.ashwake.domain.repository.character.CharacterRepository
import dev.ashwake.domain.repository.routines.FocusRepository
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

data class FocusRunState(
    val active: Boolean = false,
    val running: Boolean = false,
    val mode: FocusMode = FocusMode.POMODORO,
    val phase: FocusPhase = FocusPhase.WORK,
    val phaseIndex: Int = 0,
    val pomodoroNumber: Int = 1,
    val elapsedSeconds: Int = 0,
    val plannedSeconds: Int = 0,
    val sessionId: Long = 0,
    val taskId: Long? = null,
    val taskTitle: String? = null,
    val completedPomodoros: Int = 0,
    val whiteNoiseId: String? = null
) {
    val remainingSeconds: Int
        get() = if (mode == FocusMode.STOPWATCH) 0
        else (plannedSeconds - elapsedSeconds).coerceAtLeast(0)

    val progress: Float
        get() = if (plannedSeconds <= 0) 0f
        else (elapsedSeconds.toFloat() / plannedSeconds).coerceIn(0f, 1f)
}

/**
 * Помодоро и секундомер (п. 8).
 *
 * Как и рутина, живёт в синглтоне: таймер обязан идти при закрытом экране,
 * и то же состояние показывает уведомление foreground-сервиса.
 *
 * Монеты начисляются только за рабочие фазы: платить за перерыв
 * означало бы поощрять простой.
 */
@Singleton
class FocusController @Inject constructor(
    private val focus: FocusRepository,
    private val character: CharacterRepository,
    private val planner: PomodoroPlanner,
    private val speaker: StepSpeaker,
    private val clock: AppClock
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    private val _state = MutableStateFlow(FocusRunState())
    val state: StateFlow<FocusRunState> = _state.asStateFlow()

    private val _config = MutableStateFlow(PomodoroConfig())
    val config: StateFlow<PomodoroConfig> = _config.asStateFlow()

    fun setConfig(config: PomodoroConfig) { _config.value = config }

    fun start(
        mode: FocusMode,
        taskId: Long? = null,
        taskTitle: String? = null,
        projectId: Long? = null,
        whiteNoiseId: String? = null
    ) {
        scope.launch {
            val phase = planner.phaseAt(0, _config.value)
            val planned = if (mode == FocusMode.STOPWATCH) 0 else phase.durationSeconds

            val sessionId = focus.start(
                mode = mode,
                phase = if (mode == FocusMode.STOPWATCH) FocusPhase.WORK else phase.phase,
                plannedSeconds = planned,
                taskId = taskId,
                projectId = projectId,
                whiteNoiseId = whiteNoiseId
            )

            _state.value = FocusRunState(
                active = true,
                running = true,
                mode = mode,
                phase = if (mode == FocusMode.STOPWATCH) FocusPhase.WORK else phase.phase,
                phaseIndex = 0,
                pomodoroNumber = 1,
                plannedSeconds = planned,
                sessionId = sessionId,
                taskId = taskId,
                taskTitle = taskTitle,
                whiteNoiseId = whiteNoiseId
            )
            speaker.prepare()
            startTicker()
        }
    }

    fun pause() {
        ticker?.cancel()
        ticker = null
        _state.update { it.copy(running = false) }
        // Пауза считается прерыванием: по их числу видно, насколько сессия рваная
        val sessionId = _state.value.sessionId
        if (sessionId != 0L) scope.launch { focus.addInterruption(sessionId) }
    }

    fun resume() {
        if (!_state.value.active) return
        _state.update { it.copy(running = true) }
        startTicker()
    }

    /** Досрочный переход к следующей фазе. */
    fun skipPhase() {
        scope.launch { completePhase(completed = false) }
    }

    fun stop() {
        ticker?.cancel()
        ticker = null
        scope.launch {
            val current = _state.value
            if (current.sessionId != 0L) {
                focus.finish(current.sessionId, current.elapsedSeconds, completed = false)
                grantIfWork(current.phase, current.elapsedSeconds, completed = false)
            }
            _state.value = FocusRunState()
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (true) {
                delay(1_000)
                val current = _state.value
                if (!current.running || !current.active) break

                val elapsed = current.elapsedSeconds + 1
                _state.update { it.copy(elapsedSeconds = elapsed) }

                // Секундомер не заканчивается сам: его останавливает человек
                if (current.mode == FocusMode.POMODORO && elapsed >= current.plannedSeconds) {
                    completePhase(completed = true)
                    break
                }
            }
        }
    }

    private suspend fun completePhase(completed: Boolean) {
        val current = _state.value
        if (current.sessionId != 0L) {
            focus.finish(current.sessionId, current.elapsedSeconds, completed)
            grantIfWork(current.phase, current.elapsedSeconds, completed)
        }
        speaker.vibrate(200)

        if (current.mode == FocusMode.STOPWATCH) {
            _state.value = FocusRunState()
            return
        }

        val nextIndex = current.phaseIndex + 1
        val next = planner.phaseAt(nextIndex, _config.value)
        val completedPomodoros =
            current.completedPomodoros + if (current.phase == FocusPhase.WORK && completed) 1 else 0

        val sessionId = focus.start(
            mode = FocusMode.POMODORO,
            phase = next.phase,
            plannedSeconds = next.durationSeconds,
            taskId = current.taskId,
            projectId = null,
            whiteNoiseId = current.whiteNoiseId
        )

        _state.value = current.copy(
            running = _config.value.autoStartNext,
            phase = next.phase,
            phaseIndex = nextIndex,
            pomodoroNumber = next.pomodoroNumber,
            elapsedSeconds = 0,
            plannedSeconds = next.durationSeconds,
            sessionId = sessionId,
            completedPomodoros = completedPomodoros
        )
        if (_config.value.autoStartNext) startTicker()
    }

    /** Награда только за рабочую фазу и только если она реально шла. */
    private suspend fun grantIfWork(phase: FocusPhase, seconds: Int, completed: Boolean) {
        if (phase != FocusPhase.WORK) return
        if (!completed && seconds < MIN_REWARDABLE_SECONDS) return

        character.grantReward(
            RewardContext(
                source = RewardSource.FOCUS_DONE,
                time = clock.now().atZone(clock.zone()).toLocalTime()
            )
        )
        character.grantStatPoints(StatSource.FOCUS_SESSION)
    }

    private companion object {
        /** Пять минут: короче — это не сессия фокуса, а случайное нажатие. */
        const val MIN_REWARDABLE_SECONDS = 300
    }
}
