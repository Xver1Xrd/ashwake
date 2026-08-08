package dev.ashwake.data.importer

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Разбор CSV.
 *
 * Своя реализация, а не библиотека: нужен ровно один разбор строки в поля,
 * зато с кавычками, запятыми и переводами строк внутри значения —
 * в экспортах TickTick и Todoist заметки с переносами встречаются постоянно,
 * и наивный `split(",")` разваливает такой файл молча.
 */
@Singleton
class CsvReader @Inject constructor() {

    /** @return строки файла как списки полей. Пустые строки пропускаются. */
    fun parse(content: String, separator: Char = ','): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val field = StringBuilder()
        val row = mutableListOf<String>()

        var inQuotes = false
        var index = 0

        while (index < content.length) {
            val char = content[index]

            when {
                inQuotes && char == '"' && content.getOrNull(index + 1) == '"' -> {
                    // Удвоенная кавычка внутри значения — это одна кавычка
                    field.append('"')
                    index++
                }

                char == '"' -> inQuotes = !inQuotes

                !inQuotes && char == separator -> {
                    row += field.toString()
                    field.clear()
                }

                !inQuotes && (char == '\n' || char == '\r') -> {
                    // \r\n — один разрыв, а не два
                    if (char == '\r' && content.getOrNull(index + 1) == '\n') index++
                    row += field.toString()
                    field.clear()
                    if (row.any { it.isNotBlank() }) rows += row.toList()
                    row.clear()
                }

                else -> field.append(char)
            }
            index++
        }

        row += field.toString()
        if (row.any { it.isNotBlank() }) rows += row.toList()

        return rows
    }

    /**
     * Разбор с заголовком: строки превращаются в карты «колонка → значение».
     *
     * @param skipLinesBefore сколько строк пропустить до заголовка — TickTick
     *        кладёт перед ним служебную преамбулу.
     */
    fun parseWithHeader(
        content: String,
        separator: Char = ',',
        skipLinesBefore: Int = 0
    ): List<Map<String, String>> {
        val rows = parse(content, separator).drop(skipLinesBefore)
        val header = rows.firstOrNull() ?: return emptyList()

        return rows.drop(1).map { row ->
            header.mapIndexed { index, name ->
                name.trim() to (row.getOrNull(index)?.trim().orEmpty())
            }.toMap()
        }
    }

    /** Индекс строки-заголовка по обязательной колонке: преамбула бывает разной длины. */
    fun findHeaderRow(content: String, requiredColumn: String, separator: Char = ','): Int {
        parse(content, separator).forEachIndexed { index, row ->
            if (row.any { it.trim().equals(requiredColumn, ignoreCase = true) }) return index
        }
        return -1
    }
}
