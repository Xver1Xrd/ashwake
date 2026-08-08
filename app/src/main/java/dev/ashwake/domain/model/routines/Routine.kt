package dev.ashwake.domain.model.routines

import java.time.Instant
import java.time.LocalTime

data class RoutineStep(
    val id: Long = 0,
    val routineId: Long = 0,
    val title: String,
    val durationSeconds: Int,
    val position: Int = 0,
    val note: String? = null,
    /** Что произносит TTS, если заголовок читается плохо. */
    val ttsText: String? = null
) {
    val spokenText: String get() = ttsText?.takeIf { it.isNotBlank() } ?: title
}

data class Routine(
    val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val startTime: LocalTime? = null,
    val alarmEnabled: Boolean = false,
    val ttsEnabled: Boolean = true,
    val vibrateOnStep: Boolean = true,
    val position: Int = 0,
    val archived: Boolean = false,
    val steps: List<RoutineStep> = emptyList()
) {
    val plannedSeconds: Int get() = steps.sumOf { it.durationSeconds }
}

/**
 * Шаг внутри сессии.
 *
 * Заголовок и плановая длительность копируются из рутины на момент запуска:
 * иначе правка рутины переписала бы историю прошлых прохождений задним числом.
 */
data class RoutineSessionStep(
    val id: Long = 0,
    val sessionId: Long = 0,
    val stepId: Long? = null,
    val title: String,
    val position: Int,
    val plannedSeconds: Int,
    val actualSeconds: Int = 0,
    val skipped: Boolean = false,
    val addedOnTheFly: Boolean = false
) {
    /** Отклонение факта от плана: положительное — просрочка. */
    val deviationSeconds: Int get() = actualSeconds - plannedSeconds
}

data class RoutineSession(
    val id: Long = 0,
    val routineId: Long,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val completed: Boolean = false,
    val plannedSeconds: Int = 0,
    val actualSeconds: Int = 0,
    val skippedSteps: Int = 0,
    val steps: List<RoutineSessionStep> = emptyList()
) {
    /** Пройдена без пропуска шагов — условие бонуса routineBonus (п. 16.5.2). */
    val flawless: Boolean get() = completed && skippedSteps == 0
}
