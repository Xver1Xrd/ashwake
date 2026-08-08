package dev.ashwake.domain.engine.focus

import dev.ashwake.domain.model.focus.FocusPhase
import dev.ashwake.domain.model.focus.PomodoroConfig
import javax.inject.Inject
import javax.inject.Singleton

data class PomodoroPhase(
    val phase: FocusPhase,
    val durationSeconds: Int,
    /** Номер рабочего помидора, к которому относится фаза, начиная с 1. */
    val pomodoroNumber: Int
)

/**
 * Последовательность фаз помодоро (п. 8).
 *
 * Чистый класс без таймера: он решает только «что дальше и на сколько»,
 * а тикает уже сервис. Благодаря этому длинный перерыв каждые N помидоров
 * проверяется тестом, а не наблюдением за экраном полтора часа.
 */
@Singleton
class PomodoroPlanner @Inject constructor() {

    /**
     * Фаза по порядковому номеру.
     *
     * Индексация сквозная: 0 — первая работа, 1 — первый перерыв, и так далее.
     * Длинный перерыв ставится после каждого [PomodoroConfig.longBreakEvery]
     * помидора, то есть за четвёртым, восьмым и так далее.
     */
    fun phaseAt(index: Int, config: PomodoroConfig): PomodoroPhase {
        val safeIndex = index.coerceAtLeast(0)
        val isWork = safeIndex % 2 == 0
        val pomodoroNumber = safeIndex / 2 + 1

        if (isWork) {
            return PomodoroPhase(
                phase = FocusPhase.WORK,
                durationSeconds = config.workMinutes * 60,
                pomodoroNumber = pomodoroNumber
            )
        }

        val completed = pomodoroNumber
        val longBreak = config.longBreakEvery > 0 && completed % config.longBreakEvery == 0
        return PomodoroPhase(
            phase = if (longBreak) FocusPhase.LONG_BREAK else FocusPhase.BREAK,
            durationSeconds = (if (longBreak) config.longBreakMinutes else config.breakMinutes) * 60,
            pomodoroNumber = pomodoroNumber
        )
    }

    /** Первые [count] фаз — нужно превью «что дальше» на экране фокуса. */
    fun sequence(count: Int, config: PomodoroConfig): List<PomodoroPhase> =
        (0 until count.coerceAtLeast(0)).map { phaseAt(it, config) }

    /** Сколько чистой работы в первых [phases] фазах. Без перерывов. */
    fun workSecondsIn(phases: Int, config: PomodoroConfig): Int =
        sequence(phases, config).filter { it.phase == FocusPhase.WORK }
            .sumOf { it.durationSeconds }
}

/** Заголовок фазы для интерфейса. Вынесен из класса: инъекция ради строки не нужна. */
fun phaseTitle(phase: FocusPhase): String = when (phase) {
    FocusPhase.WORK -> "Работа"
    FocusPhase.BREAK -> "Перерыв"
    FocusPhase.LONG_BREAK -> "Длинный перерыв"
}
