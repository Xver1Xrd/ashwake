package dev.ashwake.domain.model.focus

import java.time.Instant

enum class FocusMode { POMODORO, STOPWATCH }

enum class FocusPhase { WORK, BREAK, LONG_BREAK }

data class FocusSession(
    val id: Long = 0,
    val taskId: Long? = null,
    val projectId: Long? = null,
    val mode: FocusMode = FocusMode.POMODORO,
    val phase: FocusPhase = FocusPhase.WORK,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    /** У секундомера плана нет — поле остаётся нулём. */
    val plannedSeconds: Int = 0,
    val actualSeconds: Int = 0,
    val completed: Boolean = false,
    val interruptions: Int = 0,
    val whiteNoiseId: String? = null
) {
    val isWork: Boolean get() = phase == FocusPhase.WORK
}

/** Настройки помодоро. Длительности настраиваются (п. 8). */
data class PomodoroConfig(
    val workMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val longBreakEvery: Int = 4,
    val autoStartNext: Boolean = true
)

/** Сводка фокуса для статистики: по дням недели и по проектам (п. 8). */
data class FocusStats(
    val totalSeconds: Long,
    val sessions: Int,
    val byWeekday: Map<Int, Long>,
    val byProject: Map<Long?, Long>,
    val byTask: Map<Long?, Long>
)
