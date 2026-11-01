package dev.ashwake.ui.tasks

import dev.ashwake.core.model.Priority
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.model.tasks.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskDayGroupingTest {

    private val today = LocalDate.of(2026, 9, 10)

    @Test
    fun `разбивка по дням группирует просроченные сегодня завтра и без срока`() {
        val tasks = listOf(
            task(1, "Просроченная задача", due = today.minusDays(2)),
            task(2, "Задача на сегодня", due = today),
            task(3, "Задача на завтра", due = today.plusDays(1)),
            task(4, "Задача в субботу", due = today.plusDays(2)),
            task(5, "Задача без срока", due = null)
        )

        val groups = groupTasksByDay(tasks, today)

        assertEquals(5, groups.size)
        assertEquals(today.minusDays(2), groups[0].date)
        assertTrue(groups[0].isOverdue)
        assertEquals(1, groups[0].tasks.size)

        assertTrue(groups[1].title.startsWith("Сегодня"))
        assertTrue(groups[1].isToday)
        assertEquals(1, groups[1].tasks.size)

        assertTrue(groups[2].title.startsWith("Завтра"))
        assertEquals(1, groups[2].tasks.size)

        assertTrue(groups[3].title.startsWith("Суббота"))
        assertEquals(1, groups[3].tasks.size)

        assertEquals("Без срока", groups[4].title)
        assertEquals(1, groups[4].tasks.size)
    }

    @Test
    fun `пустой список возвращает пустые группы`() {
        val groups = groupTasksByDay(emptyList(), today)
        assertTrue(groups.isEmpty())
    }

    @Test
    fun `выполненные и активные задачи сохраняют общий порядок дат`() {
        val monday = LocalDate.of(2026, 10, 19)
        val tasks = (0..6).map { day ->
            task(day.toLong(), "Задача", monday.plusDays(day.toLong())).copy(
                status = if (day == 3 || day == 4) TaskStatus.DONE else TaskStatus.ACTIVE
            )
        }.reversed()
        val groups = groupTasksByDay(tasks, monday.plusDays(7))
        assertEquals((0..6).map { monday.plusDays(it.toLong()) }, groups.map { it.date })
        assertEquals(7, groups.sumOf { it.tasks.size })
    }

    private fun task(id: Long, title: String, due: LocalDate?) = Task(
        id = id,
        title = title,
        dueDate = due,
        priority = Priority.P2,
        status = TaskStatus.ACTIVE
    )
}
