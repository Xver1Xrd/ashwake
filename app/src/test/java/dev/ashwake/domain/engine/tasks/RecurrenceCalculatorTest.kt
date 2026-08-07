package dev.ashwake.domain.engine.tasks

import dev.ashwake.core.model.WeekdayMask
import dev.ashwake.domain.model.tasks.RecurrenceRule
import dev.ashwake.domain.model.tasks.RecurrenceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceCalculatorTest {

    private val calculator = RecurrenceCalculator()
    private val start = LocalDate.of(2026, 1, 1)

    @Test
    fun `ежедневно даёт следующий день`() {
        val rule = RecurrenceRule(type = RecurrenceType.DAILY, startDate = start)
        assertEquals(LocalDate.of(2026, 1, 8), calculator.next(rule, LocalDate.of(2026, 1, 7)))
    }

    @Test
    fun `каждые N дней держатся исходной сетки, а не даты закрытия`() {
        // Каждые 3 дня от 1 января: 4, 7, 10, 13...
        // Задачу закрыли с опозданием 9-го — следующая всё равно 10-е, а не 12-е.
        val rule = RecurrenceRule(
            type = RecurrenceType.EVERY_N_DAYS,
            intervalDays = 3,
            startDate = start
        )
        assertEquals(LocalDate.of(2026, 1, 10), calculator.next(rule, LocalDate.of(2026, 1, 9)))
    }

    @Test
    fun `с флагом fromCompletion отсчёт идёт от факта`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.EVERY_N_DAYS,
            intervalDays = 3,
            startDate = start,
            fromCompletion = true
        )
        assertEquals(LocalDate.of(2026, 1, 12), calculator.next(rule, LocalDate.of(2026, 1, 9)))
    }

    @Test
    fun `по дням недели выбирает ближайший подходящий`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.WEEKDAYS,
            weekdays = WeekdayMask.of(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
            startDate = start
        )
        // 7 января 2026 — среда, ближайший из пн/чт — четверг 8-го
        assertEquals(LocalDate.of(2026, 1, 8), calculator.next(rule, LocalDate.of(2026, 1, 7)))
        // от четверга — понедельник 12-го
        assertEquals(LocalDate.of(2026, 1, 12), calculator.next(rule, LocalDate.of(2026, 1, 8)))
    }

    @Test
    fun `пустая маска дней недели не зацикливается`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.WEEKDAYS,
            weekdays = WeekdayMask.NONE,
            startDate = start
        )
        assertNull(calculator.next(rule, start))
    }

    @Test
    fun `N-е число месяца пропускает короткие месяцы, а не съезжает на конец`() {
        // «Каждое 31-е» не должно превращаться в «каждое последнее число».
        val rule = RecurrenceRule(
            type = RecurrenceType.DAY_OF_MONTH,
            dayOfMonth = 31,
            startDate = start
        )
        assertEquals(LocalDate.of(2026, 3, 31), calculator.next(rule, LocalDate.of(2026, 1, 31)))
    }

    @Test
    fun `N-е число месяца в обычном случае`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.DAY_OF_MONTH,
            dayOfMonth = 15,
            startDate = start
        )
        assertEquals(LocalDate.of(2026, 2, 15), calculator.next(rule, LocalDate.of(2026, 1, 15)))
    }

    @Test
    fun `правило истекает по endDate`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.DAILY,
            startDate = start,
            endDate = LocalDate.of(2026, 1, 10)
        )
        assertEquals(LocalDate.of(2026, 1, 10), calculator.next(rule, LocalDate.of(2026, 1, 9)))
        assertNull(calculator.next(rule, LocalDate.of(2026, 1, 10)))
    }

    @Test
    fun `список повторов в интервале`() {
        val rule = RecurrenceRule(
            type = RecurrenceType.EVERY_N_DAYS,
            intervalDays = 7,
            startDate = start
        )
        val dates = calculator.occurrencesBetween(
            rule,
            from = LocalDate.of(2026, 1, 1),
            to = LocalDate.of(2026, 2, 1)
        )
        // Первый повтор — сама дата старта: она тоже принадлежит серии
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 8), LocalDate.of(2026, 1, 15),
                LocalDate.of(2026, 1, 22), LocalDate.of(2026, 1, 29)
            ),
            dates
        )
    }
}
