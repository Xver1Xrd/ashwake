package dev.ashwake.domain.engine.focus

import dev.ashwake.domain.model.focus.FocusPhase
import dev.ashwake.domain.model.focus.PomodoroConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class PomodoroPlannerTest {

    private val planner = PomodoroPlanner()
    private val config = PomodoroConfig()

    @Test
    fun `чётные фазы это работа, нечётные перерыв`() {
        assertEquals(FocusPhase.WORK, planner.phaseAt(0, config).phase)
        assertEquals(FocusPhase.BREAK, planner.phaseAt(1, config).phase)
        assertEquals(FocusPhase.WORK, planner.phaseAt(2, config).phase)
    }

    @Test
    fun `длительности берутся из настроек`() {
        val custom = PomodoroConfig(workMinutes = 50, breakMinutes = 10)
        assertEquals(50 * 60, planner.phaseAt(0, custom).durationSeconds)
        assertEquals(10 * 60, planner.phaseAt(1, custom).durationSeconds)
    }

    @Test
    fun `длинный перерыв встаёт после каждого четвёртого помидора`() {
        // Индексы перерывов: 1, 3, 5, 7 — после 1, 2, 3 и 4-го помидора
        assertEquals(FocusPhase.BREAK, planner.phaseAt(1, config).phase)
        assertEquals(FocusPhase.BREAK, planner.phaseAt(3, config).phase)
        assertEquals(FocusPhase.BREAK, planner.phaseAt(5, config).phase)
        assertEquals(FocusPhase.LONG_BREAK, planner.phaseAt(7, config).phase)
        assertEquals(15 * 60, planner.phaseAt(7, config).durationSeconds)
    }

    @Test
    fun `восьмой помидор снова даёт длинный перерыв`() {
        assertEquals(FocusPhase.LONG_BREAK, planner.phaseAt(15, config).phase)
    }

    @Test
    fun `частота длинного перерыва настраивается`() {
        val custom = config.copy(longBreakEvery = 2)
        assertEquals(FocusPhase.BREAK, planner.phaseAt(1, custom).phase)
        assertEquals(FocusPhase.LONG_BREAK, planner.phaseAt(3, custom).phase)
    }

    @Test
    fun `нулевая частота отключает длинные перерывы`() {
        val custom = config.copy(longBreakEvery = 0)
        (1..21 step 2).forEach { index ->
            assertEquals(FocusPhase.BREAK, planner.phaseAt(index, custom).phase)
        }
    }

    @Test
    fun `номер помидора растёт правильно`() {
        assertEquals(1, planner.phaseAt(0, config).pomodoroNumber)
        assertEquals(1, planner.phaseAt(1, config).pomodoroNumber)
        assertEquals(2, planner.phaseAt(2, config).pomodoroNumber)
        assertEquals(3, planner.phaseAt(4, config).pomodoroNumber)
    }

    @Test
    fun `чистая работа считается без перерывов`() {
        // Четыре помидора это восемь фаз: 4 * 25 минут работы
        assertEquals(4 * 25 * 60, planner.workSecondsIn(8, config))
    }

    @Test
    fun `отрицательный индекс не ломает расчёт`() {
        assertEquals(FocusPhase.WORK, planner.phaseAt(-5, config).phase)
    }

    @Test
    fun `последовательность нужной длины`() {
        assertEquals(6, planner.sequence(6, config).size)
        assertEquals(0, planner.sequence(0, config).size)
    }

    @Test
    fun `заголовки фаз на русском`() {
        assertEquals("Работа", phaseTitle(FocusPhase.WORK))
        assertEquals("Перерыв", phaseTitle(FocusPhase.BREAK))
        assertEquals("Длинный перерыв", phaseTitle(FocusPhase.LONG_BREAK))
    }
}
