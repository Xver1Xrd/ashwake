package dev.ashwake.domain.engine.routines

import dev.ashwake.domain.model.routines.Routine
import dev.ashwake.domain.model.routines.RoutineSession
import dev.ashwake.domain.model.routines.RoutineSessionStep
import dev.ashwake.domain.model.routines.RoutineStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RoutineProgressCalculatorTest {

    private val calculator = RoutineProgressCalculator()

    private fun step(
        title: String,
        planned: Int,
        actual: Int = 0,
        position: Int = 0,
        skipped: Boolean = false,
        added: Boolean = false
    ) = RoutineSessionStep(
        title = title,
        position = position,
        plannedSeconds = planned,
        actualSeconds = actual,
        skipped = skipped,
        addedOnTheFly = added
    )

    @Test
    fun `остаток текущего шага и всей рутины`() {
        val steps = listOf(
            step("умыться", 120, actual = 120, position = 0),
            step("зарядка", 300, position = 1),
            step("душ", 600, position = 2)
        )
        val progress = calculator.progress(steps, stepIndex = 1, stepElapsedSeconds = 100)

        assertEquals(200, progress.stepRemainingSeconds)
        // 200 остаток текущего + 600 третьего
        assertEquals(800, progress.totalRemainingSeconds)
        assertEquals(220, progress.totalElapsedSeconds)
    }

    @Test
    fun `пройденное считается по факту, а не по плану`() {
        // Первый шаг закрыт за 60 секунд вместо 120 — учитываем факт
        val steps = listOf(
            step("умыться", 120, actual = 60, position = 0),
            step("зарядка", 300, position = 1)
        )
        val progress = calculator.progress(steps, stepIndex = 1, stepElapsedSeconds = 0)
        assertEquals(60, progress.totalElapsedSeconds)
    }

    @Test
    fun `прогресс шага не выходит за единицу при просрочке`() {
        val steps = listOf(step("шаг", 60, position = 0))
        val progress = calculator.progress(steps, stepIndex = 0, stepElapsedSeconds = 300)

        assertEquals(1f, progress.stepProgress, 0.001f)
        assertEquals(0, progress.stepRemainingSeconds)
    }

    @Test
    fun `пустая рутина не падает`() {
        val progress = calculator.progress(emptyList(), stepIndex = 0, stepElapsedSeconds = 0)
        assertEquals(0, progress.totalRemainingSeconds)
        assertEquals(1f, progress.totalProgress, 0.001f)
    }

    @Test
    fun `индекс за пределами списка зажимается`() {
        val steps = listOf(step("a", 60, position = 0), step("b", 60, position = 1))
        val progress = calculator.progress(steps, stepIndex = 99, stepElapsedSeconds = 0)
        assertEquals(1, progress.stepIndex)
    }

    @Test
    fun `итог считает отклонение и пропуски`() {
        val session = RoutineSession(
            routineId = 1,
            startedAt = Instant.EPOCH,
            completed = true,
            steps = listOf(
                step("умыться", 120, actual = 100, position = 0),
                step("зарядка", 300, actual = 420, position = 1),
                step("душ", 600, actual = 0, position = 2, skipped = true)
            )
        )
        val summary = calculator.summarize(session)

        assertEquals(1020, summary.plannedSeconds)
        assertEquals(520, summary.actualSeconds)
        assertEquals(-500, summary.deviationSeconds)
        assertEquals(1, summary.skippedSteps)
        assertEquals(2, summary.completedSteps)
        assertFalse(summary.flawless)
    }

    @Test
    fun `заметными считаются только шаги с отклонением от минуты`() {
        val session = RoutineSession(
            routineId = 1,
            startedAt = Instant.EPOCH,
            steps = listOf(
                step("почти по плану", 300, actual = 320, position = 0),
                step("сильно дольше", 300, actual = 600, position = 1)
            )
        )
        val summary = calculator.summarize(session)

        assertEquals(1, summary.notableSteps.size)
        assertEquals("сильно дольше", summary.notableSteps.first().title)
    }

    @Test
    fun `сессия без пропусков считается безупречной`() {
        val session = RoutineSession(
            routineId = 1,
            startedAt = Instant.EPOCH,
            completed = true,
            steps = listOf(step("a", 60, actual = 60, position = 0))
        )
        assertTrue(calculator.summarize(session).flawless)
    }

    @Test
    fun `снимок шагов копирует план и порядок`() {
        val routine = Routine(
            id = 1, name = "Утро",
            steps = listOf(
                RoutineStep(id = 2, title = "второй", durationSeconds = 60, position = 1),
                RoutineStep(id = 1, title = "первый", durationSeconds = 120, position = 0)
            )
        )
        val snapshot = calculator.snapshotSteps(routine)

        assertEquals(listOf("первый", "второй"), snapshot.map { it.title })
        assertEquals(listOf(0, 1), snapshot.map { it.position })
        assertEquals(120, snapshot.first().plannedSeconds)
    }

    @Test
    fun `правка длительности на ходу не уводит шаг в ноль`() {
        assertEquals(360, calculator.adjustDuration(300, 1))
        assertEquals(10, calculator.adjustDuration(300, -10))
        assertEquals(900, calculator.adjustDuration(300, 10))
    }
}
