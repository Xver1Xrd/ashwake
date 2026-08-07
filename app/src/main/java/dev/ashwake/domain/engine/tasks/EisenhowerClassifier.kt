package dev.ashwake.domain.engine.tasks

import dev.ashwake.core.model.Priority
import dev.ashwake.domain.model.tasks.EisenhowerQuadrant
import dev.ashwake.domain.model.tasks.Task
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Раскладка задач по матрице Эйзенхауэра.
 *
 * Важность берём из приоритета (P1/P2 — важно), срочность — из дедлайна.
 * Ручной перенос drag-and-drop записывается в [Task.quadrantOverride] и всегда
 * побеждает: пользователь видит свою раскладку, а не пересчитанную.
 */
@Singleton
class EisenhowerClassifier @Inject constructor() {

    /** Задача считается срочной, если дедлайн сегодня/завтра или уже просрочен. */
    fun isUrgent(task: Task, today: LocalDate): Boolean {
        val due = task.dueDate ?: return false
        return ChronoUnit.DAYS.between(today, due) <= URGENT_HORIZON_DAYS
    }

    fun isImportant(task: Task): Boolean =
        task.priority == Priority.P1 || task.priority == Priority.P2

    fun quadrantOf(task: Task, today: LocalDate): EisenhowerQuadrant {
        task.quadrantOverride?.let { return it }
        val urgent = isUrgent(task, today)
        val important = isImportant(task)
        return when {
            urgent && important -> EisenhowerQuadrant.URGENT_IMPORTANT
            !urgent && important -> EisenhowerQuadrant.NOT_URGENT_IMPORTANT
            urgent -> EisenhowerQuadrant.URGENT_NOT_IMPORTANT
            else -> EisenhowerQuadrant.NOT_URGENT_NOT_IMPORTANT
        }
    }

    fun group(tasks: List<Task>, today: LocalDate): Map<EisenhowerQuadrant, List<Task>> =
        EisenhowerQuadrant.entries.associateWith { quadrant ->
            tasks.filter { quadrantOf(it, today) == quadrant }
        }

    /**
     * Приоритет, соответствующий квадранту. Используется при перетаскивании:
     * бросив задачу в «срочное и важное», пользователь ожидает, что она станет P1,
     * а не останется P4 с чужим цветом.
     */
    fun priorityFor(quadrant: EisenhowerQuadrant): Priority = when (quadrant) {
        EisenhowerQuadrant.URGENT_IMPORTANT -> Priority.P1
        EisenhowerQuadrant.NOT_URGENT_IMPORTANT -> Priority.P2
        EisenhowerQuadrant.URGENT_NOT_IMPORTANT -> Priority.P3
        EisenhowerQuadrant.NOT_URGENT_NOT_IMPORTANT -> Priority.P4
    }

    private companion object {
        const val URGENT_HORIZON_DAYS = 1L
    }
}
