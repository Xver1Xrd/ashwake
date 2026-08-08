package dev.ashwake.domain.engine.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class WeeklyReportBuilderTest {

    private val builder = WeeklyReportBuilder()
    private val weekStart = LocalDate.of(2026, 3, 2)

    private fun report(
        tasks: Long = 10, tasksPrev: Long = 8,
        marks: Long = 20, marksPrev: Long = 15,
        expected: Long = 25, expectedPrev: Long = 25,
        focus: Long = 3600, focusPrev: Long = 1800,
        moods: List<Int> = listOf(3, 4, 5),
        energies: List<Int> = emptyList()
    ) = builder.build(
        weekStart = weekStart,
        tasksCompleted = tasks, tasksCompletedPrevious = tasksPrev,
        habitMarks = marks, habitMarksPrevious = marksPrev,
        habitExpected = expected, habitExpectedPrevious = expectedPrev,
        focusSeconds = focus, focusSecondsPrevious = focusPrev,
        ritualDays = 5,
        scoreDelta = mapOf("зарядка" to 0.05f),
        abstinenceDays = mapOf("не курю" to 42L),
        moods = moods, energies = energies
    )

    @Test
    fun `неделя охватывает семь дней`() {
        val result = report()
        assertEquals(weekStart, result.weekStart)
        assertEquals(weekStart.plusDays(6), result.weekEnd)
    }

    @Test
    fun `сравнение с прошлой неделей считается в процентах`() {
        val result = report(tasks = 12, tasksPrev = 8)
        assertEquals(4L, result.tasksCompleted.delta)
        assertEquals(50, result.tasksCompleted.percent)
    }

    @Test
    fun `без прошлой недели процент не выдумывается`() {
        val result = report(tasksPrev = 0)
        assertNull(result.tasksCompleted.percent)
        assertFalse(result.tasksCompleted.hasPrevious)
    }

    @Test
    fun `доля выполнения привычек считается от ожидаемых отметок`() {
        val result = report(marks = 20, expected = 25)
        assertEquals(0.8f, result.habitCompletionRate, 0.001f)
    }

    @Test
    fun `доля не превышает единицу при переработке`() {
        val result = report(marks = 30, expected = 25)
        assertEquals(1f, result.habitCompletionRate, 0.001f)
    }

    @Test
    fun `без ожидаемых отметок доля нулевая, а не бесконечная`() {
        val result = report(marks = 5, expected = 0)
        assertEquals(0f, result.habitCompletionRate, 0.001f)
    }

    @Test
    fun `средние по шкалам считаются только по заполненным дням`() {
        val result = report(moods = listOf(2, 4), energies = emptyList())
        assertEquals(3f, result.averageMood!!, 0.001f)
        assertNull(result.averageEnergy)
    }

    @Test
    fun `изменение доли выполнения показывает динамику`() {
        val result = report(marks = 20, expected = 25, marksPrev = 10, expectedPrev = 25)
        assertEquals(0.4f, result.completionRateDelta, 0.001f)
    }
}

class YearSummaryBuilderTest {

    private val builder = YearSummaryBuilder()

    private fun summary(
        streaks: Map<String, Int> = mapOf("зарядка" to 40, "чтение" to 12),
        scores: Map<String, Float> = mapOf("зарядка" to 0.85f, "чтение" to 0.3f),
        monthly: Map<YearMonth, Int> = mapOf(
            YearMonth.of(2026, 1) to 100,
            YearMonth.of(2026, 2) to 40,
            YearMonth.of(2026, 3) to 0
        )
    ) = builder.build(
        year = 2026,
        tasksCompleted = 312,
        habitMarks = 900,
        streaks = streaks,
        habitScores = scores,
        monthlyActivity = monthly,
        focusSeconds = 360_000,
        cleanDays = 200,
        relapseReasons = mapOf("стресс" to 3, "компания" to 7),
        activityByDate = mapOf(LocalDate.of(2026, 1, 1) to 5)
    )

    @Test
    fun `самый длинный стрик берётся с именем привычки`() {
        val result = summary()
        assertEquals(40, result.longestStreak)
        assertEquals("зарядка", result.longestStreakHabit)
    }

    @Test
    fun `самая стабильная и самая проблемная выбираются по score`() {
        val result = summary()
        assertEquals("зарядка", result.mostStableHabit)
        assertEquals("чтение", result.mostProblematicHabit)
    }

    @Test
    fun `привычка без истории не становится самой проблемной`() {
        // Заведена вчера, score ещё нулевой — титул ей не положен
        val result = summary(scores = mapOf("зарядка" to 0.85f, "новая" to 0f))
        assertEquals("зарядка", result.mostProblematicHabit)
    }

    @Test
    fun `худший месяц выбирается среди месяцев с активностью`() {
        // Март с нулём — это ещё не прожитый месяц, а не провал
        val result = summary()
        assertEquals(YearMonth.of(2026, 1), result.bestMonth)
        assertEquals(YearMonth.of(2026, 2), result.worstMonth)
    }

    @Test
    fun `причины срывов отсортированы по частоте`() {
        val result = summary()
        assertEquals(listOf("компания", "стресс"), result.relapseReasons.keys.toList())
    }

    @Test
    fun `пустой год не считается годом с данными`() {
        val empty = builder.build(
            year = 2026, tasksCompleted = 0, habitMarks = 0,
            streaks = emptyMap(), habitScores = emptyMap(), monthlyActivity = emptyMap(),
            focusSeconds = 0, cleanDays = 0,
            relapseReasons = emptyMap(), activityByDate = emptyMap()
        )
        assertFalse(empty.hasData)
        assertNull(empty.mostStableHabit)
        assertEquals(0, empty.longestStreak)
    }

    @Test
    fun `год с данными помечается как непустой`() {
        assertTrue(summary().hasData)
    }
}
