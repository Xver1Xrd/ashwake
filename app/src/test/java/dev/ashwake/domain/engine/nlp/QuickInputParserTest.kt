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

    @Test
    fun `разбирает 12-часовой формат с am и pm`() {
        val resultPm = parser.parse("созвон 6pm", today)
        assertEquals(LocalTime.of(18, 0), resultPm.time)
        assertEquals("созвон", resultPm.title)

        val resultPmSpace = parser.parse("обед в 1:30 pm", today)
        assertEquals(LocalTime.of(13, 30), resultPmSpace.time)
        assertEquals("обед", resultPmSpace.title)

        val resultAm = parser.parse("планёрка 9 am", today)
        assertEquals(LocalTime.of(9, 0), resultAm.time)
        assertEquals("планёрка", resultAm.title)
    }

    @Test
    fun `разбирает сложный пример с опечаткой, 12-часовым временем и подзадачами через запятую`() {
        val input = "завттра в 5 вечера сходить в магазин, купить молоко, сливки, спаржу(200 рублей)"
        val result = parser.parse(input, today)

        assertEquals("сходить в магазин", result.title)
        assertEquals(LocalDate.of(2026, 1, 8), result.date)
        assertEquals(LocalTime.of(17, 0), result.time)
        assertEquals("🛒", result.emoji)
        assertEquals(
            listOf("купить молоко", "сливки", "спаржу(200 рублей)"),
            result.subtasks
        )
    }

    @Test
    fun `разбирает 12-часовые разговорные форматы на русском`() {
        assertEquals(LocalTime.of(15, 0), parser.parse("созвон в 3 дня", today).time)
        assertEquals(LocalTime.of(10, 0), parser.parse("планёрка в 10 утра", today).time)
        assertEquals(LocalTime.of(21, 0), parser.parse("кино в 9 вечера", today).time)
        assertEquals(LocalTime.of(23, 0), parser.parse("сон в 11 ночи", today).time)
        assertEquals(LocalTime.of(13, 0), parser.parse("встреча в обед", today).time)
        assertEquals(LocalTime.of(12, 0), parser.parse("митинг в полдень", today).time)
        assertEquals(LocalTime.of(0, 0), parser.parse("созвон в полночь", today).time)
    }

    @Test
    fun `разбирает относительное время через N минут или часов`() {
        val baseTime = LocalTime.of(14, 0)
        assertEquals(LocalTime.of(14, 30), parser.parse("кофе через полчаса", today, baseTime).time)
        assertEquals(LocalTime.of(15, 0), parser.parse("кофе через час", today, baseTime).time)
        assertEquals(LocalTime.of(16, 0), parser.parse("созвон через 2 часа", today, baseTime).time)
        assertEquals(LocalTime.of(14, 15), parser.parse("созвон через 15 минут", today, baseTime).time)
    }

    @Test
    fun `разбирает подзадачи через двоеточие`() {
        val result = parser.parse("Купить продукты: молоко, хлеб, сыр", today)
        assertEquals("Купить продукты", result.title)
        assertEquals(listOf("молоко", "хлеб", "сыр"), result.subtasks)
        assertEquals("🛒", result.emoji)
    }

    @Test
    fun `разбирает естественные приоритеты и сложности`() {
        val p1 = parser.parse("срочно починить сервер *5", today)
        assertEquals(Priority.P1, p1.priority)
        assertEquals(5, p1.difficulty)
        assertEquals("починить сервер", p1.title)

        val p2 = parser.parse("важно подготовить отчёт легко", today)
        assertEquals(Priority.P2, p2.priority)
        assertEquals(2, p2.difficulty)

        val diff3 = parser.parse("задача средне", today)
        assertEquals(3, diff3.difficulty)
    }

    @Test
    fun `разбирает естественные оценки времени и проекты`() {
        val res = parser.parse("тренировка на 45 минут @спорт", today)
        assertEquals(45, res.estimateMinutes)
        assertEquals("спорт", res.projectName)
        assertEquals("🏋️", res.emoji)

        val resHours = parser.parse("работа на 2 часа", today)
        assertEquals(120, resHours.estimateMinutes)
    }

    @Test
    fun `распознает смысловые эмодзи по корням слов`() {
        assertEquals("📞", parser.parse("позвонить коллеге", today).emoji)
        assertEquals("🩺", parser.parse("визит к врачу", today).emoji)
        assertEquals("🏃", parser.parse("пробежка в парке", today).emoji)
        assertEquals("💳", parser.parse("оплатить налоги", today).emoji)
        assertEquals("💻", parser.parse("пофиксить код", today).emoji)
        assertEquals("✉️", parser.parse("написать письмо", today).emoji)
    }
}
