package dev.ashwake.data.importer

import dev.ashwake.core.model.Priority
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.tasks.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CsvReaderTest {

    private val reader = CsvReader()

    @Test
    fun `простые строки разбираются на поля`() {
        val rows = reader.parse("a,b,c\n1,2,3")
        assertEquals(listOf(listOf("a", "b", "c"), listOf("1", "2", "3")), rows)
    }

    @Test
    fun `запятая внутри кавычек не разрывает поле`() {
        val rows = reader.parse("a,\"б, в\",г")
        assertEquals(listOf(listOf("a", "б, в", "г")), rows)
    }

    @Test
    fun `перевод строки внутри кавычек не разрывает строку`() {
        // Заметки с переносами есть в каждом экспорте — наивный split их ломает
        val rows = reader.parse("a,\"первая\nвторая\",c")
        assertEquals(1, rows.size)
        assertEquals("первая\nвторая", rows.first()[1])
    }

    @Test
    fun `удвоенная кавычка превращается в одну`() {
        val rows = reader.parse("a,\"он сказал \"\"да\"\"\",c")
        assertEquals("он сказал \"да\"", rows.first()[1])
    }

    @Test
    fun `windows-переводы строк не дают пустых строк`() {
        val rows = reader.parse("a,b\r\n1,2\r\n")
        assertEquals(2, rows.size)
    }

    @Test
    fun `заголовок превращает строки в карты`() {
        val rows = reader.parseWithHeader("Title,Priority\nЗадача,5")
        assertEquals("Задача", rows.first()["Title"])
        assertEquals("5", rows.first()["Priority"])
    }

    @Test
    fun `строка заголовка ищется по обязательной колонке`() {
        val content = "Date: 2026-01-01\nВерсия: 1\nTitle,Priority\nЗадача,5"
        assertEquals(2, reader.findHeaderRow(content, "Title"))
    }

    @Test
    fun `пустой файл не падает`() {
        assertTrue(reader.parse("").isEmpty())
        assertTrue(reader.parseWithHeader("").isEmpty())
    }
}

class LoopCsvParserTest {

    private val parser = LoopCsvParser(CsvReader())

    private val content = """
        Date,Зарядка,Чтение
        2026-01-01,2,0
        2026-01-02,2,2
        2026-01-03,-1,2
    """.trimIndent()

    @Test
    fun `привычки берутся из заголовка`() {
        val result = parser.parse(content)
        assertEquals(listOf("Зарядка", "Чтение"), result.habits.map { it.name })
    }

    @Test
    fun `двойка означает выполнено, ноль — пропуск`() {
        val result = parser.parse(content)
        val first = result.entries.first { it.habitName == "Зарядка" && it.date == LocalDate.of(2026, 1, 1) }
        val second = result.entries.first { it.habitName == "Чтение" && it.date == LocalDate.of(2026, 1, 1) }

        assertEquals(EntryStatus.DONE, first.status)
        assertEquals(EntryStatus.SKIPPED, second.status)
    }

    @Test
    fun `минус единица означает отсутствие данных, а не пропуск`() {
        // Иначе импорт превратил бы «трекер ещё не вёлся» в череду провалов
        val result = parser.parse(content)
        val third = result.entries.filter { it.date == LocalDate.of(2026, 1, 3) }

        assertEquals(1, third.size)
        assertEquals("Чтение", third.first().habitName)
    }

    @Test
    fun `битая дата пропускает строку целиком`() {
        val broken = "Date,Зарядка\nне-дата,2\n2026-01-05,2"
        val result = parser.parse(broken)
        assertEquals(1, result.entries.size)
    }

    @Test
    fun `пустой файл не падает`() {
        val result = parser.parse("")
        assertTrue(result.habits.isEmpty())
        assertTrue(result.entries.isEmpty())
    }
}

class TickTickCsvParserTest {

    private val parser = TickTickCsvParser(CsvReader())

    private val content = """
        "Date: 2026-01-01"
        "Version: 1"
        "Folder Name","List Name","Title","Content","Priority","Status","Due Date"
        "Работа","Проект","Позвонить в банк","заметка","5","0","2026-02-01T10:00:00+0300"
        "Работа","Проект","Готово","","3","2",""
        "Работа","Проект","","","0","0",""
    """.trimIndent()

    @Test
    fun `преамбула пропускается, задачи читаются`() {
        val (tasks, report) = parser.parse(content)
        assertEquals(2, tasks.size)
        assertEquals(2, report.imported)
    }

    @Test
    fun `приоритет пересчитывается в нашу шкалу`() {
        val (tasks, _) = parser.parse(content)
        assertEquals(Priority.P1, tasks.first { it.title == "Позвонить в банк" }.priority)
        assertEquals(Priority.P2, tasks.first { it.title == "Готово" }.priority)
    }

    @Test
    fun `статус два означает выполненную задачу`() {
        val (tasks, _) = parser.parse(content)
        assertEquals(TaskStatus.DONE, tasks.first { it.title == "Готово" }.status)
        assertEquals(TaskStatus.ACTIVE, tasks.first { it.title == "Позвонить в банк" }.status)
    }

    @Test
    fun `дата и время разбираются из смещения`() {
        val (tasks, _) = parser.parse(content)
        val task = tasks.first { it.title == "Позвонить в банк" }

        assertEquals(LocalDate.of(2026, 2, 1), task.dueDate)
        assertEquals(10, task.dueTime?.hour)
    }

    @Test
    fun `пустое название попадает в отчёт, а не исчезает молча`() {
        val (_, report) = parser.parse(content)
        assertEquals(1, report.skipped)
        assertTrue(report.reasons.containsKey("Пустое название"))
    }

    @Test
    fun `файл без заголовка даёт понятный отчёт`() {
        val (tasks, report) = parser.parse("что-то не то")
        assertTrue(tasks.isEmpty())
        assertTrue(report.reasons.keys.first().contains("заголовок"))
    }
}

class TodoistCsvParserTest {

    private val parser = TodoistCsvParser(CsvReader())

    private val content = """
        TYPE,CONTENT,DESCRIPTION,PRIORITY,DATE
        task,Купить молоко,,4,2026-03-01
        section,Раздел,,1,
        task,Позвонить,описание,2,
        note,Комментарий,,1,
        task,,,1,
    """.trimIndent()

    @Test
    fun `задачи читаются, разделы и комментарии пропускаются`() {
        val (tasks, report) = parser.parse(content)

        assertEquals(2, tasks.size)
        assertEquals(3, report.skipped)
        assertTrue(report.reasons.containsKey("Разделы не импортируются"))
        assertTrue(report.reasons.containsKey("Комментарии не импортируются"))
    }

    @Test
    fun `шкала приоритета переворачивается`() {
        // У Todoist 4 — самый срочный, у нас P1
        val (tasks, _) = parser.parse(content)
        assertEquals(Priority.P1, tasks.first { it.title == "Купить молоко" }.priority)
        assertEquals(Priority.P3, tasks.first { it.title == "Позвонить" }.priority)
    }

    @Test
    fun `дата разбирается, пустая остаётся пустой`() {
        val (tasks, _) = parser.parse(content)
        assertEquals(LocalDate.of(2026, 3, 1), tasks.first { it.title == "Купить молоко" }.dueDate)
        assertNull(tasks.first { it.title == "Позвонить" }.dueDate)
    }

    @Test
    fun `предпросмотр показывает распознанные названия`() {
        val (_, report) = parser.parse(content)
        assertEquals(listOf("Купить молоко", "Позвонить"), report.previewTitles)
    }
}
