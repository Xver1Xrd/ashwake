package dev.ashwake.ui.tasks

import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.model.tasks.TaskStatus
import dev.ashwake.ui.components.FlameLevel
import dev.ashwake.ui.tasks.components.difficultyDescription
import dev.ashwake.ui.tasks.components.formatEstimateRu
import dev.ashwake.ui.today.TodayUiState
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskEnhancementsTest {

    @Test
    fun `formatEstimateRu formats correctly for minutes and hours`() {
        assertEquals("", formatEstimateRu(0))
        assertEquals("45 мин", formatEstimateRu(45))
        assertEquals("1 ч", formatEstimateRu(60))
        assertEquals("1 ч 30 мин", formatEstimateRu(90))
        assertEquals("2 ч", formatEstimateRu(120))
        assertEquals("2 ч 5 мин", formatEstimateRu(125))
    }

    @Test
    fun `difficulty descriptions match 1 to 5 scale`() {
        assertEquals("Очень просто", difficultyDescription(1))
        assertEquals("Просто", difficultyDescription(2))
        assertEquals("Средне", difficultyDescription(3))
        assertEquals("Сложно", difficultyDescription(4))
        assertEquals("Очень сложно", difficultyDescription(5))
    }

    @Test
    fun `flameLevel decays in 1 day and recovers in 3 days`() {
        val dummyTaskDone = Task(id = 1, title = "Done Task", status = TaskStatus.DONE)

        // День без активности после активности -> угасает (DIM)
        val fadingState = TodayUiState(
            tasks = emptyList(),
            habits = emptyList(),
            activeYesterday = true,
            activeTwoDaysAgo = true
        )
        assertEquals(FlameLevel.DIM, fadingState.flameLevel)

        // Два дня без активности -> погас (ASH)
        val deadState = TodayUiState(
            tasks = emptyList(),
            habits = emptyList(),
            activeYesterday = false,
            activeTwoDaysAgo = false
        )
        assertEquals(FlameLevel.ASH, deadState.flameLevel)

        // 1 день активности -> искра (EMBER)
        val day1State = TodayUiState(
            tasks = listOf(dummyTaskDone),
            habits = emptyList(),
            activeYesterday = false,
            activeTwoDaysAgo = false
        )
        assertEquals(FlameLevel.EMBER, day1State.flameLevel)

        // 2 дня активности подряд -> разгорается (BURNING)
        val day2State = TodayUiState(
            tasks = listOf(dummyTaskDone),
            habits = emptyList(),
            activeYesterday = true,
            activeTwoDaysAgo = false
        )
        assertEquals(FlameLevel.BURNING, day2State.flameLevel)

        // 3 дня активности подряд -> полное пламя, восстановился (BLAZING)
        val day3State = TodayUiState(
            tasks = listOf(dummyTaskDone),
            habits = emptyList(),
            activeYesterday = true,
            activeTwoDaysAgo = true
        )
        assertEquals(FlameLevel.BLAZING, day3State.flameLevel)
    }
}
