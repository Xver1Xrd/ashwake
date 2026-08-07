package dev.ashwake.domain.engine.tasks

import dev.ashwake.core.model.Priority
import dev.ashwake.domain.model.tasks.EisenhowerQuadrant
import dev.ashwake.domain.model.tasks.Task
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EisenhowerClassifierTest {

    private val classifier = EisenhowerClassifier()
    private val today = LocalDate.of(2026, 1, 7)

    private fun task(
        priority: Priority = Priority.P4,
        due: LocalDate? = null,
        override: EisenhowerQuadrant? = null
    ) = Task(id = 1, title = "t", priority = priority, dueDate = due, quadrantOverride = override)

    @Test
    fun `дедлайн сегодня и высокий приоритет — первый квадрант`() {
        assertEquals(
            EisenhowerQuadrant.URGENT_IMPORTANT,
            classifier.quadrantOf(task(Priority.P1, today), today)
        )
    }

    @Test
    fun `важно, но дедлайн далеко — второй квадрант`() {
        assertEquals(
            EisenhowerQuadrant.NOT_URGENT_IMPORTANT,
            classifier.quadrantOf(task(Priority.P2, today.plusDays(10)), today)
        )
    }

    @Test
    fun `просроченная задача считается срочной`() {
        assertEquals(
            EisenhowerQuadrant.URGENT_NOT_IMPORTANT,
            classifier.quadrantOf(task(Priority.P3, today.minusDays(3)), today)
        )
    }

    @Test
    fun `без дедлайна и без приоритета — четвёртый квадрант`() {
        assertEquals(
            EisenhowerQuadrant.NOT_URGENT_NOT_IMPORTANT,
            classifier.quadrantOf(task(), today)
        )
    }

    @Test
    fun `ручной перенос всегда побеждает расчёт`() {
        val manual = task(Priority.P4, null, EisenhowerQuadrant.URGENT_IMPORTANT)
        assertEquals(EisenhowerQuadrant.URGENT_IMPORTANT, classifier.quadrantOf(manual, today))
    }

    @Test
    fun `квадранту соответствует свой приоритет`() {
        assertEquals(Priority.P1, classifier.priorityFor(EisenhowerQuadrant.URGENT_IMPORTANT))
        assertEquals(Priority.P4, classifier.priorityFor(EisenhowerQuadrant.NOT_URGENT_NOT_IMPORTANT))
    }

    @Test
    fun `группировка раскладывает все задачи и не теряет ни одной`() {
        val tasks = listOf(
            task(Priority.P1, today),
            task(Priority.P2, today.plusDays(10)),
            task(Priority.P3, today.minusDays(1)),
            task()
        )
        val grouped = classifier.group(tasks, today)
        assertEquals(4, grouped.values.sumOf { it.size })
        assertEquals(4, grouped.keys.size)
    }
}
