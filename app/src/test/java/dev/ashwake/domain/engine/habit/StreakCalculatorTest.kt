package dev.ashwake.domain.engine.habit

import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitEntry
import dev.ashwake.domain.model.habits.HabitSchedule
import dev.ashwake.domain.model.habits.HabitScheduleType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreakCalculatorTest {

    private val resolver = HabitScheduleResolver()
    private val calculator = StreakCalculator(resolver)

    private val start = LocalDate.of(2026, 1, 1)
    private val habit = Habit(id = 1, name = "тест", schedule = HabitSchedule(HabitScheduleType.DAILY))

    private fun entries(vararg pairs: Pair<LocalDate, EntryStatus>): Map<LocalDate, HabitEntry> =
        pairs.associate { (date, status) ->
            date to HabitEntry(habitId = 1, date = date, status = status, value = 1f)
        }

    private fun done(days: Int, offset: Long = 0): Map<LocalDate, HabitEntry> =
        (0 until days).associate { index ->
            val date = start.plusDays(offset + index)
            date to HabitEntry(habitId = 1, date = date, status = EntryStatus.DONE, value = 1f)
        }

    @Test
    fun `подряд выполненные дни складываются в серию`() {
        val result = calculator.compute(
            habit, done(10), from = start, today = start.plusDays(9)
        )
        assertEquals(10, result.current)
        assertEquals(10, result.record)
    }

    @Test
    fun `неотмеченный сегодняшний день серию не рвёт`() {
        // Отмечено 5 дней, сегодня шестой и он ещё пустой
        val result = calculator.compute(
            habit, done(5), from = start, today = start.plusDays(5)
        )
        assertEquals(5, result.current)
    }

    @Test
    fun `пропуск в прошлом обнуляет текущую серию, но рекорд остаётся`() {
        val map = done(7) + done(2, offset = 8)
        val result = calculator.compute(
            habit, map, from = start, today = start.plusDays(9)
        )
        assertEquals(2, result.current)
        assertEquals(7, result.record)
    }

    @Test
    fun `заморозка не рвёт серию и сама в неё не входит`() {
        val map = done(3) + done(3, offset = 4)
        val frozen = setOf(start.plusDays(3))
        val result = calculator.compute(
            habit, map, excludedDays = frozen, from = start, today = start.plusDays(6)
        )
        assertEquals(6, result.current)
    }

    @Test
    fun `минимум держит серию наравне с полным выполнением`() {
        val map = entries(
            start to EntryStatus.DONE,
            start.plusDays(1) to EntryStatus.MINIMUM,
            start.plusDays(2) to EntryStatus.DONE
        )
        val result = calculator.compute(habit, map, from = start, today = start.plusDays(2))
        assertEquals(3, result.current)
    }

    @Test
    fun `явный пропуск серию рвёт`() {
        val map = entries(
            start to EntryStatus.DONE,
            start.plusDays(1) to EntryStatus.SKIPPED,
            start.plusDays(2) to EntryStatus.DONE
        )
        val result = calculator.compute(habit, map, from = start, today = start.plusDays(2))
        assertEquals(1, result.current)
        assertEquals(1, result.record)
    }

    @Test
    fun `пустая история даёт нулевую серию`() {
        val result = calculator.compute(habit, emptyMap(), from = start, today = start.plusDays(5))
        assertEquals(0, result.current)
        assertEquals(0, result.record)
    }
}
