package dev.ashwake.domain.engine.timebox

import dev.ashwake.core.model.Priority
import dev.ashwake.domain.model.tasks.BlockKind
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.model.tasks.TimeboxBlock
import dev.ashwake.domain.model.tasks.TimeboxSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TimeboxPlannerTest {

    private val planner = TimeboxPlanner()
    private val date = LocalDate.of(2026, 6, 1)

    /** Рабочий день 9:00–19:00, буфер 10 минут, обед выключен — чтобы не мешал. */
    private val settings = TimeboxSettings(
        workStartMinute = 9 * 60,
        workEndMinute = 19 * 60,
        bufferMinutes = 10,
        lunchStartMinute = null
    )

    private fun task(
        id: Long,
        title: String = "задача $id",
        estimate: Int? = 60,
        priority: Priority = Priority.P3,
        due: LocalDate? = null,
        time: LocalTime? = null
    ) = Task(
        id = id,
        title = title,
        estimateMinutes = estimate,
        priority = priority,
        dueDate = due,
        dueTime = time
    )

    private fun busy(start: Int, end: Int, kind: BlockKind = BlockKind.CALENDAR) =
        TimeboxBlock(
            date = date, startMinute = start, endMinute = end,
            kind = kind, title = "занято", pinned = true
        )

    // --- свободные слоты ----------------------------------------------------

    @Test
    fun `пустой день это один слот на всё окно`() {
        val slots = planner.freeSlots(540, 1140, emptyList())
        assertEquals(1, slots.size)
        assertEquals(600, slots.first().durationMinutes)
    }

    @Test
    fun `занятый интервал режет окно надвое`() {
        val slots = planner.freeSlots(540, 1140, listOf(busy(600, 660)))
        assertEquals(listOf(540 to 600, 660 to 1140), slots.map { it.startMinute to it.endMinute })
    }

    @Test
    fun `пересекающиеся занятые интервалы сливаются`() {
        // Два наложившихся события не должны дать слот отрицательной длины
        val slots = planner.freeSlots(540, 1140, listOf(busy(600, 700), busy(650, 780)))
        assertEquals(listOf(540 to 600, 780 to 1140), slots.map { it.startMinute to it.endMinute })
    }

    @Test
    fun `занятое за пределами окна игнорируется`() {
        val slots = planner.freeSlots(540, 1140, listOf(busy(0, 300), busy(1200, 1300)))
        assertEquals(1, slots.size)
        assertEquals(540, slots.first().startMinute)
    }

    @Test
    fun `полностью занятое окно не даёт слотов`() {
        assertTrue(planner.freeSlots(540, 1140, listOf(busy(500, 1200))).isEmpty())
    }

    // --- раскладка ----------------------------------------------------------

    @Test
    fun `задачи встают подряд с буфером между ними`() {
        val result = planner.plan(
            PlanRequest(
                date = date,
                tasks = listOf(task(1, estimate = 60), task(2, estimate = 30)),
                settings = settings
            )
        )
        val blocks = result.blocks.filter { it.kind == BlockKind.TASK }.sortedBy { it.startMinute }

        assertEquals(540, blocks[0].startMinute)
        assertEquals(600, blocks[0].endMinute)
        // Буфер 10 минут: следующая начинается в 10:10, а не встык
        assertEquals(610, blocks[1].startMinute)
        assertEquals(640, blocks[1].endMinute)
    }

    @Test
    fun `сначала просроченное, потом по приоритету`() {
        val result = planner.plan(
            PlanRequest(
                date = date,
                tasks = listOf(
                    task(1, "низкий приоритет", priority = Priority.P4),
                    task(2, "высокий приоритет", priority = Priority.P1),
                    task(3, "просрочено", priority = Priority.P4, due = date.minusDays(2))
                ),
                settings = settings
            )
        )
        val order = result.blocks
            .filter { it.kind == BlockKind.TASK }
            .sortedBy { it.startMinute }
            .map { it.title }

        assertEquals(listOf("просрочено", "высокий приоритет", "низкий приоритет"), order)
    }

    @Test
    fun `при равном приоритете длинная задача идёт первой`() {
        val result = planner.plan(
            PlanRequest(
                date = date,
                tasks = listOf(task(1, "короткая", estimate = 30), task(2, "длинная", estimate = 120)),
                settings = settings
            )
        )
        val first = result.blocks.filter { it.kind == BlockKind.TASK }.minByOrNull { it.startMinute }
        assertEquals("длинная", first?.title)
    }

    @Test
    fun `дефицит считается честно, а не молча отбрасывается`() {
        // Окно 600 минут, задач на 900 — три часа не влезают
        val tasks = (1..9).map { task(it.toLong(), estimate = 100) }
        val result = planner.plan(PlanRequest(date, tasks, settings = settings))

        assertFalse(result.fitsCompletely)
        assertTrue("дефицит должен быть положительным", result.deficitMinutes > 0)
        assertEquals(
            result.deferred.sumOf { it.estimateMinutes ?: 0 },
            result.deficitMinutes
        )
    }

    @Test
    fun `задачи без оценки времени в раскладку не попадают`() {
        val result = planner.plan(
            PlanRequest(
                date = date,
                tasks = listOf(task(1, estimate = null), task(2, estimate = 60)),
                settings = settings
            )
        )
        assertEquals(1, result.withoutEstimate.size)
        assertEquals(1, result.blocks.count { it.kind == BlockKind.TASK })
    }

    @Test
    fun `задача с точным временем встаёт на своё место и закрепляется`() {
        val result = planner.plan(
            PlanRequest(
                date = date,
                tasks = listOf(task(1, "созвон", estimate = 30, time = LocalTime.of(15, 0))),
                settings = settings
            )
        )
        val block = result.blocks.first { it.taskId == 1L }

        assertEquals(15 * 60, block.startMinute)
        assertEquals(15 * 60 + 30, block.endMinute)
        assertTrue(block.pinned)
    }

    @Test
    fun `занятые интервалы обходятся стороной`() {
        val result = planner.plan(
            PlanRequest(
                date = date,
                tasks = listOf(task(1, estimate = 120)),
                busy = listOf(busy(540, 600)),
                settings = settings
            )
        )
        val block = result.blocks.first { it.kind == BlockKind.TASK }
        assertEquals(600, block.startMinute)
    }

    @Test
    fun `обед добавляется как занятый интервал`() {
        val withLunch = settings.copy(lunchStartMinute = 13 * 60, lunchDurationMinutes = 60)
        val result = planner.plan(
            PlanRequest(date, listOf(task(1, estimate = 600)), settings = withLunch)
        )
        assertTrue(result.blocks.any { it.kind == BlockKind.LUNCH })
        // Шестичасовая задача в день с обедом уже не помещается
        assertFalse(result.fitsCompletely)
    }

    @Test
    fun `в прошлое не планируем`() {
        val result = planner.plan(
            PlanRequest(
                date = date,
                tasks = listOf(task(1, estimate = 60)),
                settings = settings,
                nowMinute = 15 * 60
            )
        )
        val block = result.blocks.first { it.kind == BlockKind.TASK }
        assertEquals(15 * 60, block.startMinute)
    }

    @Test
    fun `выполненные задачи не планируются`() {
        val done = task(1).copy(status = dev.ashwake.domain.model.tasks.TaskStatus.DONE)
        val result = planner.plan(PlanRequest(date, listOf(done), settings = settings))
        assertTrue(result.blocks.none { it.kind == BlockKind.TASK })
    }

    // --- пересчёт и перетаскивание -----------------------------------------

    @Test
    fun `просрочка сдвигает следующие блоки`() {
        val blocks = listOf(
            TimeboxBlock(id = 1, date = date, startMinute = 540, endMinute = 600,
                kind = BlockKind.TASK, title = "первая"),
            TimeboxBlock(id = 2, date = date, startMinute = 610, endMinute = 670,
                kind = BlockKind.TASK, title = "вторая")
        )
        // Первая заняла до 10:30 вместо 10:00
        val updated = planner.reflow(blocks, changedBlockId = 1, newEndMinute = 630, settings)
        val second = updated.first { it.id == 2L }

        assertEquals(640, second.startMinute)
        assertEquals(700, second.endMinute)
    }

    @Test
    fun `закреплённый блок при пересчёте остаётся на месте`() {
        val blocks = listOf(
            TimeboxBlock(id = 1, date = date, startMinute = 540, endMinute = 600,
                kind = BlockKind.TASK, title = "первая"),
            TimeboxBlock(id = 2, date = date, startMinute = 610, endMinute = 670,
                kind = BlockKind.CALENDAR, title = "встреча", pinned = true)
        )
        val updated = planner.reflow(blocks, changedBlockId = 1, newEndMinute = 660, settings)
        val meeting = updated.first { it.id == 2L }

        assertEquals(610, meeting.startMinute)
    }

    @Test
    fun `перетаскивание меняет время, но не длительность`() {
        val blocks = listOf(
            TimeboxBlock(id = 1, date = date, startMinute = 540, endMinute = 600,
                kind = BlockKind.TASK, title = "задача")
        )
        val moved = planner.move(blocks, 1, 720, settings).first()

        assertEquals(720, moved.startMinute)
        assertEquals(780, moved.endMinute)
        assertEquals(60, moved.durationMinutes)
    }

    @Test
    fun `перетаскивание не выносит блок за конец рабочего дня`() {
        val blocks = listOf(
            TimeboxBlock(id = 1, date = date, startMinute = 540, endMinute = 600,
                kind = BlockKind.TASK, title = "задача")
        )
        val moved = planner.move(blocks, 1, 1400, settings).first()
        assertEquals(settings.workEndMinute, moved.endMinute)
    }

    @Test
    fun `пустой список задач даёт пустую раскладку без падений`() {
        val result = planner.plan(PlanRequest(date, emptyList(), settings = settings))
        assertTrue(result.fitsCompletely)
        assertEquals(0, result.deficitMinutes)
    }
}
