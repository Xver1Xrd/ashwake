package dev.ashwake.domain.engine.nlp

import dev.ashwake.core.model.Priority
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Результат разбора строки быстрого ввода.
 * `title` — то, что осталось после вырезания всех распознанных кусков.
 */
data class ParsedQuickInput(
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val priority: Priority? = null,
    val tagNames: List<String> = emptyList(),
    val estimateMinutes: Int? = null
) {
    val isEmpty: Boolean get() = title.isBlank()
    val hasAnyMarkup: Boolean
        get() = date != null || time != null || priority != null ||
            tagNames.isNotEmpty() || estimateMinutes != null
}

/**
 * Парсер строки вида «купить молоко завтра 18:00 !p2 #дом ~30м».
 *
 * Чистый класс: ни одного импорта android.*, «сегодня» приходит параметром,
 * поэтому поведение воспроизводимо в тестах без подмены системного времени.
 *
 * Разбор идёт по токенам слева направо. Каждый матчер может съесть
 * несколько токенов подряд («через 3 дня», «15 января», «в понедельник»),
 * поэтому наивного split-и-фильтруй здесь недостаточно.
 */
@Singleton
class QuickInputParser @Inject constructor() {

    fun parse(input: String, today: LocalDate): ParsedQuickInput {
        val tokens = input.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return ParsedQuickInput(title = "")

        val titleTokens = mutableListOf<String>()
        val tags = mutableListOf<String>()
        var date: LocalDate? = null
        var time: LocalTime? = null
        var priority: Priority? = null
        var estimate: Int? = null

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]

            // Порядок матчеров важен: сначала однозначные маркеры (!p2, #тег, ~30м),
            // потом даты и время, где возможны совпадения с обычными словами.
            val tag = parseTag(token)
            if (tag != null) { tags += tag; i++; continue }

            val prio = parsePriority(token)
            if (prio != null && priority == null) { priority = prio; i++; continue }

            val est = parseEstimate(token)
            if (est != null && estimate == null) { estimate = est; i++; continue }

            if (time == null) {
                val timeMatch = parseTime(tokens, i)
                if (timeMatch != null) { time = timeMatch.value; i += timeMatch.consumed; continue }
            }

            if (date == null) {
                val dateMatch = parseDate(tokens, i, today)
                if (dateMatch != null) { date = dateMatch.value; i += dateMatch.consumed; continue }
            }

            titleTokens += token
            i++
        }

        // «завтра» без времени — задача на день без конкретного часа. Обратное неверно:
        // указано только время → это сегодня.
        if (time != null && date == null) date = today

        return ParsedQuickInput(
            title = titleTokens.joinToString(" ").trim(),
            date = date,
            time = time,
            priority = priority,
            tagNames = tags.distinct(),
            estimateMinutes = estimate
        )
    }

    // --- отдельные маркеры -------------------------------------------------

    private fun parseTag(token: String): String? {
        if (!token.startsWith('#') || token.length < 2) return null
        val name = token.drop(1).trim(*TRAILING_PUNCTUATION)
        return name.ifEmpty { null }
    }

    private fun parsePriority(token: String): Priority? {
        val m = PRIORITY_REGEX.matchEntire(token.lowercase()) ?: return null
        return when (m.groupValues[1]) {
            "1" -> Priority.P1
            "2" -> Priority.P2
            "3" -> Priority.P3
            else -> Priority.P4
        }
    }

    /** `~30м`, `~30мин`, `~1ч`, `~1.5ч`, `~90`, `~45m`, `~2h` */
    private fun parseEstimate(token: String): Int? {
        val m = ESTIMATE_REGEX.matchEntire(token.lowercase()) ?: return null
        val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val unit = m.groupValues[2]
        val minutes = when {
            unit.startsWith("ч") || unit.startsWith("h") -> amount * 60
            else -> amount // и «м/мин/m», и отсутствие единицы — минуты
        }
        val rounded = Math.round(minutes).toInt()
        return if (rounded in 1..(24 * 60)) rounded else null
    }

    // --- время -------------------------------------------------------------

    private data class Match<T>(val value: T, val consumed: Int)

    private fun parseTime(tokens: List<String>, index: Int): Match<LocalTime>? {
        var offset = 0
        // необязательный предлог: «в 18:00»
        if (tokens[index].lowercase() in TIME_PREPOSITIONS && index + 1 < tokens.size) offset = 1
        val token = tokens.getOrNull(index + offset)?.lowercase() ?: return null

        // Части суток: «утром», «вечером»
        DAY_PARTS[token]?.let { return Match(it, offset + 1) }

        HHMM_REGEX.matchEntire(token)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h > 23 || min > 59) return null
            return Match(LocalTime.of(h, min), offset + 1)
        }
        // «в 9» — только с предлогом, иначе любое число в тексте станет временем
        if (offset == 1) {
            token.toIntOrNull()?.let { h ->
                if (h in 0..23) return Match(LocalTime.of(h, 0), offset + 1)
            }
        }
        return null
    }

    // --- дата --------------------------------------------------------------

    private fun parseDate(tokens: List<String>, index: Int, today: LocalDate): Match<LocalDate>? {
        var offset = 0
        val raw = tokens[index].lowercase().trim(*TRAILING_PUNCTUATION)

        // «через N дней», «через неделю», «через месяц»
        if (raw == "через") {
            val next = tokens.getOrNull(index + 1)?.lowercase()?.trim(*TRAILING_PUNCTUATION)
                ?: return null
            next.toIntOrNull()?.let { n ->
                val unit = tokens.getOrNull(index + 2)?.lowercase().orEmpty()
                return when {
                    unit.startsWith("дн") || unit.startsWith("ден") ->
                        Match(today.plusDays(n.toLong()), 3)
                    unit.startsWith("недел") -> Match(today.plusWeeks(n.toLong()), 3)
                    unit.startsWith("месяц") -> Match(today.plusMonths(n.toLong()), 3)
                    else -> null
                }
            }
            return when {
                next.startsWith("недел") -> Match(today.plusWeeks(1), 2)
                next.startsWith("месяц") -> Match(today.plusMonths(1), 2)
                next.startsWith("дн") || next.startsWith("ден") -> Match(today.plusDays(1), 2)
                else -> null
            }
        }

        // предлог перед днём недели: «в понедельник», «во вторник»
        val word = if (raw in DATE_PREPOSITIONS && index + 1 < tokens.size) {
            offset = 1
            tokens[index + 1].lowercase().trim(*TRAILING_PUNCTUATION)
        } else raw

        RELATIVE_DAYS[word]?.let { return Match(today.plusDays(it), offset + 1) }

        weekdayOf(word)?.let { day ->
            return Match(nextWeekday(today, day), offset + 1)
        }

        // «15 января», «15 янв»
        word.toIntOrNull()?.let { dayNum ->
            if (dayNum in 1..31) {
                val monthWord = tokens.getOrNull(index + offset + 1)?.lowercase()
                val month = monthWord?.let { monthOf(it) }
                if (month != null) {
                    val candidate = safeDate(today.year, month, dayNum) ?: return null
                    val result = if (candidate < today) candidate.plusYears(1) else candidate
                    return Match(result, offset + 2)
                }
            }
        }

        // «15.01», «15.01.2026», «15/01»
        NUMERIC_DATE_REGEX.matchEntire(word)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val yearPart = m.groupValues[3]
            val year = when {
                yearPart.isEmpty() -> today.year
                yearPart.length == 2 -> 2000 + yearPart.toInt()
                else -> yearPart.toInt()
            }
            val candidate = safeDate(year, mo, d) ?: return null
            val result = if (yearPart.isEmpty() && candidate < today) candidate.plusYears(1)
            else candidate
            return Match(result, offset + 1)
        }

        return null
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    /** Ближайший такой день недели строго после сегодня: «в понедельник» — не сегодняшний. */
    private fun nextWeekday(today: LocalDate, target: DayOfWeek): LocalDate {
        var d = today.plusDays(1)
        while (d.dayOfWeek != target) d = d.plusDays(1)
        return d
    }

    /**
     * Только точное совпадение со словарём форм. Поиск по префиксу здесь опасен:
     * «среди» начинается на «сред», «марка» — на «мар», и обычные слова
     * превращались бы в даты.
     */
    private fun weekdayOf(word: String): DayOfWeek? = WEEKDAYS[word]

    private fun monthOf(word: String): Int? = MONTHS[word.trim(*TRAILING_PUNCTUATION)]

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val TRAILING_PUNCTUATION = charArrayOf(',', '.', ';', ':', '!', '?')

        val PRIORITY_REGEX = Regex("^!p?([1-4])$")
        val ESTIMATE_REGEX = Regex("^~(\\d+(?:[.,]\\d+)?)\\s*(мин|м|ч|h|m)?$")
        val HHMM_REGEX = Regex("^(\\d{1,2}):(\\d{2})$")
        val NUMERIC_DATE_REGEX = Regex("^(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2}|\\d{4}))?$")

        val TIME_PREPOSITIONS = setOf("в", "во")
        val DATE_PREPOSITIONS = setOf("в", "во")

        val RELATIVE_DAYS = mapOf(
            "сегодня" to 0L,
            "завтра" to 1L,
            "послезавтра" to 2L
        )

        val DAY_PARTS = mapOf(
            "утром" to LocalTime.of(9, 0),
            "днём" to LocalTime.of(14, 0),
            "днем" to LocalTime.of(14, 0),
            "вечером" to LocalTime.of(19, 0),
            "ночью" to LocalTime.of(23, 0)
        )

        /**
         * Дни недели: сокращения и полные формы в падежах, которые реально
         * встречаются в быстром вводе («в среду», «до пятницы»).
         */
        val WEEKDAYS: Map<String, DayOfWeek> = buildMap {
            fun put(day: DayOfWeek, vararg forms: String) = forms.forEach { put(it, day) }
            put(DayOfWeek.MONDAY, "пн", "понедельник", "понедельника")
            put(DayOfWeek.TUESDAY, "вт", "вторник", "вторника")
            put(DayOfWeek.WEDNESDAY, "ср", "среда", "среду", "среды")
            put(DayOfWeek.THURSDAY, "чт", "четверг", "четверга")
            put(DayOfWeek.FRIDAY, "пт", "пятница", "пятницу", "пятницы")
            put(DayOfWeek.SATURDAY, "сб", "суббота", "субботу", "субботы")
            put(DayOfWeek.SUNDAY, "вс", "воскресенье", "воскресенья")
        }

        val MONTHS: Map<String, Int> = buildMap {
            fun put(month: Int, vararg forms: String) = forms.forEach { put(it, month) }
            put(1, "янв", "январь", "января")
            put(2, "фев", "февраль", "февраля")
            put(3, "мар", "март", "марта")
            put(4, "апр", "апрель", "апреля")
            put(5, "май", "мая")
            put(6, "июн", "июнь", "июня")
            put(7, "июл", "июль", "июля")
            put(8, "авг", "август", "августа")
            put(9, "сен", "сентябрь", "сентября")
            put(10, "окт", "октябрь", "октября")
            put(11, "ноя", "ноябрь", "ноября")
            put(12, "дек", "декабрь", "декабря")
        }
    }
}
