package dev.ashwake.domain.engine.habit

import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitEntry
import dev.ashwake.domain.model.habits.HabitSchedule
import dev.ashwake.domain.model.habits.HabitScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitScheduleResolverTest {
    private val resolver = HabitScheduleResolver()

    @Test
    fun `одна отметка закрывает привычку до конца недели`() {
        val habit = Habit(
            id = 7,
            name = "позвонить родителям",
            schedule = HabitSchedule(HabitScheduleType.TIMES_PER_WEEK, timesPerWeek = 1)
        )
        val monday = LocalDate.of(2026, 10, 12)
        val wednesday = monday.plusDays(2)
        val entry = HabitEntry(1, habit.id, wednesday, EntryStatus.DONE)

        val before = resolver.currentPeriodState(habit, monday, emptyMap())
        val after = resolver.currentPeriodState(habit, monday.plusDays(5), mapOf(wednesday to entry))

        assertFalse(before.completed)
        assertTrue(after.completed)
        assertEquals(monday, after.start)
        assertEquals(monday.plusDays(6), after.endInclusive)
        assertEquals(wednesday, after.lastCompletionDate)
    }

    @Test
    fun `двухдневный срок сохраняет выполнение на второй день`() {
        val anchor = LocalDate.of(2026, 10, 10)
        val habit = Habit(
            id = 8,
            name = "через день",
            schedule = HabitSchedule(
                type = HabitScheduleType.EVERY_OTHER_DAY,
                intervalAnchor = anchor
            )
        )
        val entry = HabitEntry(1, habit.id, anchor, EntryStatus.DONE)

        val state = resolver.currentPeriodState(habit, anchor.plusDays(1), mapOf(anchor to entry))

        assertTrue(state.completed)
        assertEquals(anchor, state.start)
        assertEquals(anchor.plusDays(1), state.endInclusive)
    }

    @Test
    fun `двухнедельная привычка использует один четырнадцатидневный период`() {
        val anchor = LocalDate.of(2026, 10, 5)
        val habit = Habit(
            id = 9,
            name = "раз в две недели",
            schedule = HabitSchedule(
                type = HabitScheduleType.BIWEEKLY,
                biweeklyAnchor = anchor
            )
        )
        val markDate = anchor.plusDays(4)
        val entry = HabitEntry(1, habit.id, markDate, EntryStatus.DONE)

        val state = resolver.currentPeriodState(habit, anchor.plusDays(12), mapOf(markDate to entry))

        assertTrue(state.completed)
        assertEquals(anchor.plusDays(13), state.endInclusive)
    }
}
