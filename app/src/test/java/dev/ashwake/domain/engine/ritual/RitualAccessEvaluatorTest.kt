package dev.ashwake.domain.engine.ritual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RitualAccessEvaluatorTest {

    private val evaluator = RitualAccessEvaluator()
    private val date = LocalDate.of(2026, 9, 10)

    @Test
    fun `днём до 20 00 ритуал недоступен и сообщает время открытия`() {
        val dt = date.atTime(15, 30)
        val status = evaluator.evaluate(
            currentDateTime = dt,
            dayStartHour = 4,
            hasCompletedReview = { false }
        )

        assertTrue(status is RitualAccessStatus.TooEarly)
        val tooEarly = status as RitualAccessStatus.TooEarly
        assertEquals(date, tooEarly.targetDate)
        assertEquals(date.atTime(20, 0), tooEarly.opensAt)
    }

    @Test
    fun `в 19 59 ритуал ещё закрыт`() {
        val dt = date.atTime(19, 59)
        val status = evaluator.evaluate(
            currentDateTime = dt,
            dayStartHour = 4,
            hasCompletedReview = { false }
        )

        assertTrue(status is RitualAccessStatus.TooEarly)
    }

    @Test
    fun `ровно в 20 00 ритуал открывается за сегодняшний день`() {
        val dt = date.atTime(20, 0)
        val status = evaluator.evaluate(
            currentDateTime = dt,
            dayStartHour = 4,
            hasCompletedReview = { false }
        )

        assertTrue(status is RitualAccessStatus.Available)
        val available = status as RitualAccessStatus.Available
        assertEquals(date, available.targetDate)
        assertEquals(date.plusDays(1).atTime(4, 0), available.windowEnd)
    }

    @Test
    fun `если ритуал за сегодня уже пройден повторно пройти нельзя`() {
        val dt = date.atTime(21, 15)
        val status = evaluator.evaluate(
            currentDateTime = dt,
            dayStartHour = 4,
            hasCompletedReview = { completedDate -> completedDate == date }
        )

        assertTrue(status is RitualAccessStatus.AlreadyCompleted)
        val completed = status as RitualAccessStatus.AlreadyCompleted
        assertEquals(date, completed.targetDate)
        assertEquals(date.plusDays(1).atTime(20, 0), completed.nextAvailableAt)
    }

    @Test
    fun `ночью до 4 утра ритуал открыт за вчерашний день`() {
        val nextCalendarDay = date.plusDays(1)
        val dt = nextCalendarDay.atTime(2, 30) // 02:30 ночи
        val status = evaluator.evaluate(
            currentDateTime = dt,
            dayStartHour = 4,
            hasCompletedReview = { false }
        )

        assertTrue(status is RitualAccessStatus.Available)
        val available = status as RitualAccessStatus.Available
        assertEquals(date, available.targetDate)
        assertEquals(nextCalendarDay.atTime(4, 0), available.windowEnd)
    }

    @Test
    fun `ночью до 4 утра если ритуал за вчера уже был пройден вечером повторно пройти нельзя`() {
        val nextCalendarDay = date.plusDays(1)
        val dt = nextCalendarDay.atTime(1, 0)
        val status = evaluator.evaluate(
            currentDateTime = dt,
            dayStartHour = 4,
            hasCompletedReview = { completedDate -> completedDate == date }
        )

        assertTrue(status is RitualAccessStatus.AlreadyCompleted)
        val completed = status as RitualAccessStatus.AlreadyCompleted
        assertEquals(date, completed.targetDate)
        assertEquals(nextCalendarDay.atTime(20, 0), completed.nextAvailableAt)
    }

    @Test
    fun `в 4 утра окно закрывается и начинается ожидание нового дня`() {
        val nextCalendarDay = date.plusDays(1)
        val dt = nextCalendarDay.atTime(4, 0) // ровно начало нового дня
        val status = evaluator.evaluate(
            currentDateTime = dt,
            dayStartHour = 4,
            hasCompletedReview = { false }
        )

        assertTrue(status is RitualAccessStatus.TooEarly)
        val tooEarly = status as RitualAccessStatus.TooEarly
        assertEquals(nextCalendarDay, tooEarly.targetDate)
        assertEquals(nextCalendarDay.atTime(20, 0), tooEarly.opensAt)
    }

    @Test
    fun `при начале дня в полночь окно длится ровно до 23 59`() {
        val dtEvening = date.atTime(22, 0)
        val statusEvening = evaluator.evaluate(
            currentDateTime = dtEvening,
            dayStartHour = 0,
            hasCompletedReview = { false }
        )
        assertTrue(statusEvening is RitualAccessStatus.Available)

        val dtNight = date.plusDays(1).atTime(0, 30)
        val statusNight = evaluator.evaluate(
            currentDateTime = dtNight,
            dayStartHour = 0,
            hasCompletedReview = { false }
        )
        assertTrue(statusNight is RitualAccessStatus.TooEarly)
    }
}
