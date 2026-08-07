package dev.ashwake.domain.engine.habit

import dev.ashwake.core.model.WeekdayMask
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitEntry
import dev.ashwake.domain.model.habits.HabitSchedule
import dev.ashwake.domain.model.habits.HabitScheduleType
import dev.ashwake.domain.model.habits.HabitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitScoreCalculatorTest {

    private val resolver = HabitScheduleResolver()
    private val calculator = HabitScoreCalculator(resolver)

    private val start = LocalDate.of(2026, 1, 1)

    private fun habit(
        type: HabitType = HabitType.CHECK,
        schedule: HabitSchedule = HabitSchedule(HabitScheduleType.DAILY)
    ) = Habit(id = 1, name = "тест", type = type, schedule = schedule)

    private fun entries(
        dates: List<LocalDate>,
        status: EntryStatus = EntryStatus.DONE
    ): Map<LocalDate, HabitEntry> = dates.associateWith {
        HabitEntry(habitId = 1, date = it, status = status, value = 1f)
    }

    @Test
    fun `идеальная ежедневная привычка за 30 дней даёт около 80 процентов`() {
        val days = (0L until 30L).map { start.plusDays(it) }
        val score = calculator.currentScore(
            habit = habit(),
            entries = entries(days),
            from = start,
            to = start.plusDays(29)
        )
        assertEquals(0.80f, score, 0.01f)
    }

    @Test
    fun `привычка три раза в неделю растёт за месяц не медленнее ежедневной`() {
        // Три раза в неделю, норма выполняется каждую неделю.
        val schedule = HabitSchedule(HabitScheduleType.TIMES_PER_WEEK, timesPerWeek = 3)
        val target = habit(schedule = schedule)

        val done = mutableListOf<LocalDate>()
        (0L until 35L).forEach { offset ->
            val date = start.plusDays(offset)
            if (date.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)) {
                done += date
            }
        }

        val score = calculator.currentScore(
            habit = target,
            entries = entries(done),
            from = start,
            to = start.plusDays(29)
        )
        // Тот же порядок величины, что у ежедневной: масштабирование альфы работает
        assertTrue("score = $score", score > 0.7f)
    }

    @Test
    fun `несколько пропусков после длинной серии не обнуляют прогресс`() {
        val perfect = (0L until 60L).map { start.plusDays(it) }
        val scoreBefore = calculator.currentScore(
            habit = habit(), entries = entries(perfect),
            from = start, to = start.plusDays(59)
        )

        // Три пропуска подряд после двух месяцев идеального выполнения
        val withGaps = entries(perfect.filterNot { it >= start.plusDays(60) })
        val scoreAfter = calculator.currentScore(
            habit = habit(), entries = withGaps,
            from = start, to = start.plusDays(62)
        )

        assertTrue("было $scoreBefore, стало $scoreAfter", scoreAfter > scoreBefore - 0.2f)
        assertTrue("score не должен обнуляться: $scoreAfter", scoreAfter > 0.6f)
    }

    @Test
    fun `минимум даёт половинный вклад`() {
        val days = (0L until 30L).map { start.plusDays(it) }
        val full = calculator.currentScore(
            habit(), entries(days, EntryStatus.DONE), from = start, to = start.plusDays(29)
        )
        val minimum = calculator.currentScore(
            habit(), entries(days, EntryStatus.MINIMUM), from = start, to = start.plusDays(29)
        )
        assertEquals(full / 2f, minimum, 0.01f)
    }

    @Test
    fun `замороженный день не понижает score`() {
        val days = (0L until 20L).map { start.plusDays(it) }
        val frozenDay = start.plusDays(20)

        val withFreeze = calculator.currentScore(
            habit = habit(),
            entries = entries(days),
            excludedDays = setOf(frozenDay),
            from = start,
            to = frozenDay
        )
        val withoutExtraDay = calculator.currentScore(
            habit = habit(),
            entries = entries(days),
            from = start,
            to = start.plusDays(19)
        )
        assertEquals(withoutExtraDay, withFreeze, 0.0001f)
    }

    @Test
    fun `пропущенный день без заморозки score понижает`() {
        val days = (0L until 20L).map { start.plusDays(it) }
        val skipped = calculator.currentScore(
            habit = habit(), entries = entries(days),
            from = start, to = start.plusDays(20)
        )
        val clean = calculator.currentScore(
            habit = habit(), entries = entries(days),
            from = start, to = start.plusDays(19)
        )
        assertTrue("пропуск обязан снижать score", skipped < clean)
    }

    @Test
    fun `у негативной привычки день без отметки считается успехом`() {
        val negative = habit(type = HabitType.NEGATIVE)
        val score = calculator.currentScore(
            habit = negative,
            entries = emptyMap(),
            from = start,
            to = start.plusDays(29)
        )
        assertEquals(0.80f, score, 0.01f)
    }

    @Test
    fun `у негативной привычки отметка означает срыв и роняет score`() {
        val negative = habit(type = HabitType.NEGATIVE)
        val relapse = entries(listOf(start.plusDays(10)), EntryStatus.SKIPPED)
        val score = calculator.currentScore(
            habit = negative, entries = relapse, from = start, to = start.plusDays(29)
        )
        val clean = calculator.currentScore(
            habit = negative, entries = emptyMap(), from = start, to = start.plusDays(29)
        )
        assertTrue("срыв обязан снижать score", score < clean)
    }

    @Test
    fun `расписание по дням недели считает только свои дни`() {
        val schedule = HabitSchedule(
            HabitScheduleType.WEEKDAYS,
            weekdays = WeekdayMask.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        )
        val target = habit(schedule = schedule)
        val monThu = (0L until 60L)
            .map { start.plusDays(it) }
            .filter { it.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY) }

        val score = calculator.currentScore(
            habit = target, entries = entries(monThu), from = start, to = start.plusDays(29)
        )
        // Все запланированные дни выполнены — растёт так же, как ежедневная
        assertEquals(0.80f, score, 0.03f)
    }

    @Test
    fun `коэффициент сглаживания растёт при снижении частоты`() {
        val daily = calculator.alphaFor(7f)
        val thrice = calculator.alphaFor(3f)
        assertTrue("редкая привычка должна иметь больший шаг", thrice > daily)
    }
}
