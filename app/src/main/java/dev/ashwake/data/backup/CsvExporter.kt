package dev.ashwake.data.backup

import dev.ashwake.core.result.Outcome
import dev.ashwake.core.result.outcomeOf
import dev.ashwake.data.db.dao.habits.HabitDao
import dev.ashwake.data.db.dao.tasks.TaskDao
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Экспорт истории в CSV для таблиц (п. 10).
 *
 * JSON-бэкап предназначен для восстановления, CSV — для людей: открывается
 * в любом табличном редакторе. Формат минимальный и стабильный: одна строка
 * на запись, без переносов и кавычек-хаосов — значения экранируются по RFC 4180.
 */
@Singleton
class CsvExporter @Inject constructor(
    private val taskDao: TaskDao,
    private val habitDao: HabitDao
) {

    suspend fun exportTasksCsv(): Outcome<String> = outcomeOf {
        val tasks = taskDao.allTasks()
        val rows = listOf(TASK_HEADER) + tasks.map { task ->
            listOf(
                task.id.toString(),
                escape(task.title),
                task.status,
                task.priority,
                isoDate(task.completedAt),
                isoDate(task.createdAt),
                task.postponeCount.toString()
            )
        }
        rows.joinToString("\n", postfix = "\n") { row -> row.joinToString(",") }
    }

    suspend fun exportHabitsCsv(): Outcome<String> = outcomeOf {
        val habits = habitDao.allHabits().associateBy { it.id }
        val rows = listOf(HABIT_HEADER) + habitDao.allEntries().map { entry ->
            listOf(
                entry.date.toString(),
                escape(habits[entry.habitId]?.name.orEmpty()),
                entry.status,
                formatValue(entry.value)
            )
        }
        rows.joinToString("\n", postfix = "\n") { row -> row.joinToString(",") }
    }

    private fun formatValue(value: Float): String =
        if (value % 1f == 0f) value.toInt().toString() else value.toString()

    /** RFC 4180: кавычки удваиваются, поле берётся в кавычки при необходимости. */
    private fun escape(value: String): String {
        val needsQuotes = value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private fun isoDate(millis: Long?): String {
        if (millis == null) return ""
        return DateTimeFormatter.ISO_LOCAL_DATE.format(
            Instant.ofEpochMilli(millis).atOffset(ZoneOffset.UTC).toLocalDate()
        )
    }

    private companion object {
        val TASK_HEADER = listOf("id", "title", "status", "priority", "completedAt", "createdAt", "postponeCount")
        val HABIT_HEADER = listOf("date", "habit", "status", "value")
    }
}
