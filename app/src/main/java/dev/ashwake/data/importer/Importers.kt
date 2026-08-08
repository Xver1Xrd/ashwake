package dev.ashwake.data.importer

import dev.ashwake.core.model.Priority
import dev.ashwake.domain.model.habits.EntryStatus
import dev.ashwake.domain.model.habits.Habit
import dev.ashwake.domain.model.habits.HabitType
import dev.ashwake.domain.model.tasks.Task
import dev.ashwake.domain.model.tasks.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/** Источник импорта. */
enum class ImportSource { LOOP_CSV, TICKTICK_CSV, TODOIST_CSV }

/** Отметка привычки из чужого экспорта. */
data class ImportedEntry(
    val habitName: String,
    val date: LocalDate,
    val status: EntryStatus,
    val value: Float
)

data class ImportedHabits(
    val habits: List<Habit>,
    val entries: List<ImportedEntry>
)

/** Отчёт мастера импорта: сколько прошло и почему остальное нет (п. 12). */
data class ImportReport(
    val source: ImportSource,
    val imported: Int,
    val skipped: Int,
    /** Причина → сколько строк по ней пропущено. */
    val reasons: Map<String, Int> = emptyMap(),
    /** Первые распознанные записи — предпросмотр до применения. */
    val previewTitles: List<String> = emptyList()
) {
    val total: Int get() = imported + skipped
}

/**
 * Импорт истории из Loop Habit Tracker (п. 12).
 *
 * Читается `Checkmarks.csv`: первая колонка — дата, дальше по колонке
 * на привычку. Значения Loop: 2 — выполнено, 0 — нет, -1 — данных нет.
 *
 * Score по импортированной истории **не переносится, а считается заново**
 * нашим движком: чужая формула дала бы числа, которые не сходятся
 * с последующими днями.
 */
@Singleton
class LoopCsvParser @Inject constructor(
    private val csv: CsvReader
) {
    fun parse(content: String): ImportedHabits {
        val rows = csv.parse(content)
        val header = rows.firstOrNull() ?: return ImportedHabits(emptyList(), emptyList())

        val habitNames = header.drop(1).map { it.trim() }.filter { it.isNotBlank() }
        val habits = habitNames.map { name ->
            Habit(name = name, type = HabitType.CHECK)
        }

        val entries = mutableListOf<ImportedEntry>()
        rows.drop(1).forEach { row ->
            val date = parseDate(row.firstOrNull()) ?: return@forEach

            habitNames.forEachIndexed { index, name ->
                val raw = row.getOrNull(index + 1)?.trim().orEmpty()
                val value = raw.toFloatOrNull() ?: return@forEachIndexed

                // -1 у Loop означает «данных нет»: это не пропуск, а отсутствие записи
                if (value < 0f) return@forEachIndexed

                entries += ImportedEntry(
                    habitName = name,
                    date = date,
                    status = when {
                        value >= DONE_VALUE -> EntryStatus.DONE
                        value > 0f -> EntryStatus.MINIMUM
                        else -> EntryStatus.SKIPPED
                    },
                    value = value
                )
            }
        }
        return ImportedHabits(habits, entries)
    }

    private fun parseDate(raw: String?): LocalDate? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { LocalDate.parse(text) }.getOrNull()
    }

    private companion object {
        /** Loop пишет 2 для выполненной булевой привычки. */
        const val DONE_VALUE = 2f
    }
}

/**
 * Импорт задач из TickTick (п. 12).
 *
 * Перед заголовком экспорт кладёт служебную преамбулу переменной длины,
 * поэтому строка заголовка ищется по обязательной колонке, а не берётся
 * по фиксированному номеру.
 */
@Singleton
class TickTickCsvParser @Inject constructor(
    private val csv: CsvReader
) {
    fun parse(content: String): Pair<List<Task>, ImportReport> {
        val headerRow = csv.findHeaderRow(content, "Title")
        if (headerRow < 0) {
            return emptyList<Task>() to ImportReport(
                source = ImportSource.TICKTICK_CSV,
                imported = 0,
                skipped = 0,
                reasons = mapOf("Не найден заголовок с колонкой Title" to 1)
            )
        }

        val rows = csv.parseWithHeader(content, skipLinesBefore = headerRow)
        val tasks = mutableListOf<Task>()
        val reasons = mutableMapOf<String, Int>()

        rows.forEach { row ->
            val title = row["Title"].orEmpty()
            if (title.isBlank()) {
                reasons.merge("Пустое название", 1, Int::plus)
                return@forEach
            }

            val due = parseDateTime(row["Due Date"])
            tasks += Task(
                title = title,
                note = row["Content"]?.takeIf { it.isNotBlank() },
                priority = priorityOf(row["Priority"]),
                dueDate = due?.toLocalDate(),
                dueTime = due?.toLocalTime()?.takeIf { it != LocalTime.MIDNIGHT },
                status = if (row["Status"] == "2") TaskStatus.DONE else TaskStatus.ACTIVE,
                completedAt = parseDateTime(row["Completed Time"])
                    ?.toInstant(ZoneOffset.UTC)
            )
        }

        return tasks to ImportReport(
            source = ImportSource.TICKTICK_CSV,
            imported = tasks.size,
            skipped = reasons.values.sum(),
            reasons = reasons,
            previewTitles = tasks.take(PREVIEW_LIMIT).map { it.title }
        )
    }

    /** TickTick: 0 — нет, 1 — низкий, 3 — средний, 5 — высокий. */
    private fun priorityOf(raw: String?): Priority = when (raw?.trim()) {
        "5" -> Priority.P1
        "3" -> Priority.P2
        "1" -> Priority.P3
        else -> Priority.P4
    }

    /**
     * Дата и время из экспорта.
     *
     * TickTick пишет смещение слитно — `+0300`, а не `+03:00`, и штатный
     * `OffsetDateTime.parse` такую строку не берёт. Поэтому форматов несколько,
     * и они пробуются по очереди.
     */
    private fun parseDateTime(raw: String?): LocalDateTime? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        DATE_TIME_FORMATS.forEach { format ->
            runCatching { OffsetDateTime.parse(text, format).toLocalDateTime() }
                .getOrNull()?.let { return it }
        }
        return runCatching { LocalDateTime.parse(text) }.getOrNull()
            ?: runCatching {
                LocalDate.parse(text, DateTimeFormatter.ISO_DATE).atStartOfDay()
            }.getOrNull()
    }

    private companion object {
        const val PREVIEW_LIMIT = 10

        val DATE_TIME_FORMATS = listOf(
            DateTimeFormatter.ISO_OFFSET_DATE_TIME,
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
        )
    }
}

/**
 * Импорт задач из Todoist (п. 12).
 *
 * В экспорте есть строки-разделители с типом `section` и пустые — они
 * пропускаются и попадают в отчёт, а не исчезают молча.
 */
@Singleton
class TodoistCsvParser @Inject constructor(
    private val csv: CsvReader
) {
    fun parse(content: String): Pair<List<Task>, ImportReport> {
        val rows = csv.parseWithHeader(content)
        val tasks = mutableListOf<Task>()
        val reasons = mutableMapOf<String, Int>()

        rows.forEach { row ->
            val type = row["TYPE"]?.trim().orEmpty()
            val text = row["CONTENT"]?.trim().orEmpty()

            when {
                type.equals("section", ignoreCase = true) -> {
                    reasons.merge("Разделы не импортируются", 1, Int::plus)
                    return@forEach
                }
                type.equals("note", ignoreCase = true) -> {
                    reasons.merge("Комментарии не импортируются", 1, Int::plus)
                    return@forEach
                }
                text.isBlank() -> {
                    reasons.merge("Пустая строка", 1, Int::plus)
                    return@forEach
                }
            }

            tasks += Task(
                title = text,
                note = row["DESCRIPTION"]?.takeIf { it.isNotBlank() },
                priority = priorityOf(row["PRIORITY"]),
                dueDate = parseDate(row["DATE"])
            )
        }

        return tasks to ImportReport(
            source = ImportSource.TODOIST_CSV,
            imported = tasks.size,
            skipped = reasons.values.sum(),
            reasons = reasons,
            previewTitles = tasks.take(PREVIEW_LIMIT).map { it.title }
        )
    }

    /** Todoist: 4 — самый срочный, 1 — обычный. Порядок обратный нашему. */
    private fun priorityOf(raw: String?): Priority = when (raw?.trim()) {
        "4" -> Priority.P1
        "3" -> Priority.P2
        "2" -> Priority.P3
        else -> Priority.P4
    }

    private fun parseDate(raw: String?): LocalDate? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return runCatching { LocalDate.parse(text) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(text).toLocalDate() }.getOrNull()
    }

    private companion object {
        const val PREVIEW_LIMIT = 10
    }
}
