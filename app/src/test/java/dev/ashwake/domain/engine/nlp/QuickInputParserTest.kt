package dev.ashwake.domain.engine.nlp

import dev.ashwake.core.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class QuickInputParserTest {

    private val parser = QuickInputParser()

    /** Среда, 7 января 2026 — точка отсчёта для всех относительных дат. */
    private val today = LocalDate.of(2026, 1, 7)

    @Test
    fun `разбирает пример из ТЗ целиком`() {
        val result = parser.parse("купить молоко завтра 18:00 !p2 #дом ~30м", today)

        assertEquals("купить молоко", result.title)
        assertEquals(LocalDate.of(2026, 1, 8), result.date)
        assertEquals(LocalTime.of(18, 0), result.time)
        assertEquals(Priority.P2, result.priority)
        assertEquals(listOf("дом"), result.tagNames)
        assertEquals(30, result.estimateMinutes)
    }

    @Test
    fun `строка без разметки остаётся названием`() {
        val result = parser.parse("позвонить в банк", today)

        assertEquals("позвонить в банк", result.title)
        assertNull(result.date)
        assertNull(result.time)
        assertNull(result.priority)
        assertTrue(result.tagNames.isEmpty())
    }

    @Test
    fun `время без даты означает сегодня`() {
        val result = parser.parse("созвон 15:30", today)

        assertEquals("созвон", result.title)
        assertEquals(today, result.date)
        assertEquals(LocalTime.of(15, 30), result.time)
    }

    @Test
    fun `дата без времени не выдумывает час`() {
        val result = parser.parse("отчёт завтра", today)

        assertEquals(LocalDate.of(2026, 1, 8), result.date)
        assertNull(result.time)
    }

    @Test
    fun `день недели берётся следующий, а не сегодняшний`() {
        // 7 января 2026 — среда. «в среду» должно дать 14 января, а не сегодня.
        val result = parser.parse("баня в среду", today)

        assertEquals("баня", result.title)
        assertEquals(LocalDate.of(2026, 1, 14), result.date)
    }

    @Test
    fun `короткие формы дней недели работают`() {
        val result = parser.parse("тренировка пт", today)
        assertEquals(LocalDate.of(2026, 1, 9), result.date)
        assertEquals("тренировка", result.title)
    }

    @Test
    fun `короткая форма не срабатывает внутри слова`() {
        // «среди» начинается на «ср», но днём недели не является
        val result = parser.parse("разобраться среди прочего", today)

        assertNull(result.date)
        assertEquals("разобраться среди прочего", result.title)
    }

    @Test
    fun `через N дней и через неделю`() {
        assertEquals(LocalDate.of(2026, 1, 10), parser.parse("оплатить через 3 дня", today).date)
        assertEquals(LocalDate.of(2026, 1, 14), parser.parse("оплатить через неделю", today).date)
        assertEquals("оплатить", parser.parse("оплатить через 3 дня", today).title)
    }

    @Test
    fun `числовая дата без года переносится на следующий год, если уже прошла`() {
        val result = parser.parse("подарок 05.01", today)
        assertEquals(LocalDate.of(2027, 1, 5), result.date)
    }

    @Test
    fun `числовая дата с годом берётся как есть`() {
        val result = parser.parse("виза 20.03.2026", today)
        assertEquals(LocalDate.of(2026, 3, 20), result.date)
    }

    @Test
    fun `дата словом с месяцем`() {
        val result = parser.parse("день рождения 15 марта", today)
        assertEquals(LocalDate.of(2026, 3, 15), result.date)
        assertEquals("день рождения", result.title)
    }

    @Test
    fun `оценка в часах переводится в минуты`() {
        assertEquals(60, parser.parse("уборка ~1ч", today).estimateMinutes)
        assertEquals(90, parser.parse("уборка ~1.5ч", today).estimateMinutes)
        assertEquals(45, parser.parse("уборка ~45", today).estimateMinutes)
        assertEquals(20, parser.parse("уборка ~20мин", today).estimateMinutes)
    }

    @Test
    fun `несколько тегов и приоритет без буквы p`() {
        val result = parser.parse("ремонт !1 #дом #срочное", today)

        assertEquals(Priority.P1, result.priority)
        assertEquals(listOf("дом", "срочное"), result.tagNames)
        assertEquals("ремонт", result.title)
    }

    @Test
    fun `голое число не становится временем без предлога`() {
        val result = parser.parse("купить 5 яблок", today)

        assertNull(result.time)
        assertEquals("купить 5 яблок", result.title)
    }

    @Test
    fun `с предлогом число становится часом`() {
        val result = parser.parse("созвон в 9", today)

        assertEquals(LocalTime.of(9, 0), result.time)
        assertEquals("созвон", result.title)
    }

    @Test
    fun `части суток дают время по умолчанию`() {
        val result = parser.parse("пробежка утром", today)
        assertEquals(LocalTime.of(9, 0), result.time)
        assertEquals("пробежка", result.title)
    }

    @Test
    fun `пустая строка не падает`() {
        val result = parser.parse("   ", today)
        assertTrue(result.isEmpty)
    }

    @Test
    fun `некорректное время не съедается как время`() {
        val result = parser.parse("код 99:99", today)
        assertNull(result.time)
    }
}
