package dev.ashwake.domain.engine.routines

import dev.ashwake.domain.model.routines.Routine
import dev.ashwake.domain.model.routines.RoutineSession
import dev.ashwake.domain.model.routines.RoutineSessionStep
import javax.inject.Inject
import javax.inject.Singleton

/** Итог сессии: план против факта по каждому шагу (п. 6). */
data class SessionSummary(
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val deviationSeconds: Int,
    val skippedSteps: Int,
    val completedSteps: Int,
    val addedSteps: Int,
    /** Шаги, где отклонение существеннее порога — их и стоит показать первыми. */
    val notableSteps: List<RoutineSessionStep>
) {
    val overran: Boolean get() = deviationSeconds > 0
    val flawless: Boolean get() = skippedSteps == 0
}

/** Состояние выполнения: что показывает полноэкранный таймер. */
data class RunProgress(
    val stepIndex: Int,
    val stepRemainingSeconds: Int,
    val stepElapsedSeconds: Int,
    val totalRemainingSeconds: Int,
    val totalElapsedSeconds: Int,
    val stepProgress: Float,
    val totalProgress: Float
)

/**
 * Расчёты рутины: сколько осталось сейчас и что вышло по итогу.
 *
 * Чистый класс без таймера — тикает сервис, а здесь только арифметика.
 * Поэтому «сколько осталось при пропуске третьего шага» проверяется тестом,
 * а не двадцатиминутным прогоном рутины вручную.
 */
@Singleton
class RoutineProgressCalculator @Inject constructor() {

    /**
     * Текущее состояние прогона.
     *
     * @param steps шаги сессии в порядке выполнения
     * @param stepIndex индекс текущего шага
     * @param stepElapsedSeconds сколько уже идёт текущий шаг
     */
    fun progress(
        steps: List<RoutineSessionStep>,
        stepIndex: Int,
        stepElapsedSeconds: Int
    ): RunProgress {
        if (steps.isEmpty()) {
            return RunProgress(0, 0, 0, 0, 0, 1f, 1f)
        }
        val index = stepIndex.coerceIn(0, steps.lastIndex)
        val current = steps[index]

        // Пройденное считается по факту завершённых шагов, а не по их плану:
        // пропущенный шаг не должен выглядеть выполненным
        val elapsedBefore = steps.take(index).sumOf { it.actualSeconds }
        val plannedTotal = steps.sumOf { it.plannedSeconds }
        val remainingAfter = steps.drop(index + 1).sumOf { it.plannedSeconds }

        val stepRemaining = (current.plannedSeconds - stepElapsedSeconds).coerceAtLeast(0)
        val totalElapsed = elapsedBefore + stepElapsedSeconds

        return RunProgress(
            stepIndex = index,
            stepRemainingSeconds = stepRemaining,
            stepElapsedSeconds = stepElapsedSeconds,
            totalRemainingSeconds = stepRemaining + remainingAfter,
            totalElapsedSeconds = totalElapsed,
            stepProgress = ratio(stepElapsedSeconds, current.plannedSeconds),
            totalProgress = ratio(totalElapsed, plannedTotal)
        )
    }

    fun summarize(session: RoutineSession): SessionSummary {
        val steps = session.steps
        val planned = steps.sumOf { it.plannedSeconds }
        val actual = steps.sumOf { it.actualSeconds }

        return SessionSummary(
            plannedSeconds = planned,
            actualSeconds = actual,
            deviationSeconds = actual - planned,
            skippedSteps = steps.count { it.skipped },
            completedSteps = steps.count { !it.skipped },
            addedSteps = steps.count { it.addedOnTheFly },
            notableSteps = steps
                .filterNot { it.skipped }
                .filter { kotlin.math.abs(it.deviationSeconds) >= NOTABLE_DEVIATION_SECONDS }
                .sortedByDescending { kotlin.math.abs(it.deviationSeconds) }
        )
    }

    /** Снимок шагов рутины на момент запуска: правка рутины не тронет историю. */
    fun snapshotSteps(routine: Routine): List<RoutineSessionStep> =
        routine.steps.sortedBy { it.position }.mapIndexed { index, step ->
            RoutineSessionStep(
                stepId = step.id,
                title = step.title,
                position = index,
                plannedSeconds = step.durationSeconds
            )
        }

    /** ±1 и ±10 минут на ходу. Ниже нуля длительность шага не опускается. */
    fun adjustDuration(currentSeconds: Int, deltaMinutes: Int): Int =
        (currentSeconds + deltaMinutes * 60).coerceAtLeast(MIN_STEP_SECONDS)

    private fun ratio(value: Int, total: Int): Float =
        if (total <= 0) 1f else (value.toFloat() / total).coerceIn(0f, 1f)

    private companion object {
        /** Отклонение меньше минуты в итоге не показываем — это шум. */
        const val NOTABLE_DEVIATION_SECONDS = 60
        const val MIN_STEP_SECONDS = 10
    }
}
