package dev.ashwake.domain.engine.nlp

import dev.ashwake.core.model.Priority
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Результат разбора строки быстрого ввода.
 */
data class ParsedQuickInput(
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val priority: Priority? = null,
    val difficulty: Int? = null,
    val tagNames: List<String> = emptyList(),
    val projectName: String? = null,
    val estimateMinutes: Int? = null,
    val emoji: String? = null,
    val subtasks: List<String> = emptyList()
) {
    val isEmpty: Boolean get() = title.isBlank()
    val hasAnyMarkup: Boolean
        get() = date != null || time != null || priority != null ||
            difficulty != null || tagNames.isNotEmpty() || projectName != null ||
            estimateMinutes != null || emoji != null || subtasks.isNotEmpty()
}

/**
 * Умный NLP-парсер естественного языка для быстрого ввода задач.
 *
 * Поддерживает:
 * - 12-часовой формат времени: «в 5 вечера» (17:00), «в 3 дня» (15:00), «в 10 утра» (10:00), «в 11 ночи» (23:00)
 * - Числа прописью: «в пять вечера», «в три дня», «в десять утра»
 * - Относительное время: «через час», «через полчаса», «через 2 часа», «через 20 минут»
 * - Опечатки в днях: «завттра», «седня», «сегодняя», «послезавттра», «понедельнек», «пятнеца»
 * - Подзадачи: через двоеточие («Купить: хлеб, молоко») или через запятые («сходить в магазин, купить молоко, сливки, спаржу»)
 * - Сложность (1–5): «очень просто» (1), «легко» (2), «средне» (3), «сложно» (4), «хард»/«очень сложно» (5), «*3»
 * - Приоритет: «срочно» (P1), «важно» (P2), «средний приоритет» (P3), «не срочно» (P4), «!1»..«!4»
 * - Оценка времени: «на 30 минут», «на 2 часа», «на полчаса», «займет час», «~45м»
 * - Проекты: «@работа», «@дом», «проект Учеба»
 * - Теги: «#спорт», «#здоровье»
 * - Семантический подбор эмодзи: автоматическое определение значка задачи по смыслу
 */
@Singleton
class QuickInputParser @Inject constructor() {

    fun parse(input: String, today: LocalDate, currentTime: LocalTime = LocalTime.now()): ParsedQuickInput {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ParsedQuickInput(title = "")

        var workingInput = trimmed
        val extractedSubtasks = mutableListOf<String>()

        // 1. Извлечение явных подзадач через двоеточие (не являющееся частью времени 18:00)
        val colonMatch = Regex("""(?<!\d):\s*""").find(workingInput)
        if (colonMatch != null) {
            val colonIdx = colonMatch.range.first
            val head = workingInput.substring(0, colonIdx).trim()
            val tail = workingInput.substring(colonIdx + 1).trim()
            if (head.isNotBlank() && tail.isNotBlank()) {
                val parts = tail.split(Regex("[,;\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.isNotEmpty()) {
                    workingInput = head
                    extractedSubtasks.addAll(parts)
                }
            }
        }

        val rawTokens = workingInput.split(WHITESPACE).filter { it.isNotEmpty() }
        if (rawTokens.isEmpty()) return ParsedQuickInput(title = "")

        val titleTokens = mutableListOf<String>()
        val tags = mutableListOf<String>()
        var projectName: String? = null
        var date: LocalDate? = null
        var time: LocalTime? = null
        var priority: Priority? = null
        var difficulty: Int? = null
        var estimate: Int? = null

        var i = 0
        while (i < rawTokens.size) {
            val token = rawTokens[i]

            // @проект
            if (projectName == null && token.startsWith('@') && token.length > 1) {
                projectName = token.drop(1).trimEnd(*TRAILING_PUNCTUATION)
                i++
                continue
            }
            if (projectName == null && (token.equals("проект", ignoreCase = true) || token.equals("в проекте", ignoreCase = true))) {
                val next = rawTokens.getOrNull(i + 1)?.trimEnd(*TRAILING_PUNCTUATION)
                if (!next.isNullOrBlank() && !next.startsWith('#') && !next.startsWith('!')) {
                    projectName = next
                    i += 2
                    continue
                }
            }

            // #тег
            val tag = parseTag(token)
            if (tag != null) { tags += tag; i++; continue }

            // Приоритет (!p1, !1, срочно, важно...)
            if (priority == null) {
                val prioMatch = parsePriority(rawTokens, i)
                if (prioMatch != null) {
                    priority = prioMatch.value
                    i += prioMatch.consumed
                    continue
                }
            }

            // Сложность (*1..*5, сложно, легко...)
            if (difficulty == null) {
                val diffMatch = parseDifficulty(rawTokens, i)
                if (diffMatch != null) {
                    difficulty = diffMatch.value
                    i += diffMatch.consumed
                    continue
                }
            }

            // Оценка времени (~30м, на 2 часа, на полчаса...)
            if (estimate == null) {
                val estMatch = parseEstimate(rawTokens, i)
                if (estMatch != null) {
                    estimate = estMatch.value
                    i += estMatch.consumed
                    continue
                }
            }

            // Время (в 5 вечера, в 18:00, через час...)
            if (time == null) {
                val timeMatch = parseTime(rawTokens, i, currentTime)
                if (timeMatch != null) {
                    time = timeMatch.value
                    i += timeMatch.consumed
                    continue
                }
            }

            // Дата (завттра, в пятницу, на выходных...)
            if (date == null) {
                val dateMatch = parseDate(rawTokens, i, today)
                if (dateMatch != null) {
                    date = dateMatch.value
                    i += dateMatch.consumed
                    continue
                }
            }

            titleTokens += token
            i++
        }

        // Если указано время без даты — это сегодня
        if (time != null && date == null) date = today

        var rawTitle = titleTokens.joinToString(" ").trim()

        // 2. Извлечение перечислительных подзадач через запятую, если двоеточия не было
        if (extractedSubtasks.isEmpty() && rawTitle.contains(',')) {
            val commaParts = rawTitle.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            // Если есть хотя бы 2 части, и вторая часть не союзное придаточное предложение
            if (commaParts.size >= 2) {
                val second = commaParts[1].lowercase()
                val isConjunction = CLAUSE_CONJUNCTIONS.any { second.startsWith(it) }
                if (!isConjunction) {
                    // Первая часть — название задачи, остальные — подзадачи
                    rawTitle = commaParts[0]
                    extractedSubtasks.addAll(commaParts.drop(1))
                }
            }
        }

        val cleanTitle = rawTitle.trimEnd(*TRAILING_PUNCTUATION)
        val autoEmoji = detectEmoji(cleanTitle)

        return ParsedQuickInput(
            title = cleanTitle,
            date = date,
            time = time,
            priority = priority,
            difficulty = difficulty,
            tagNames = tags.distinct(),
            projectName = projectName,
            estimateMinutes = estimate,
            emoji = autoEmoji,
            subtasks = extractedSubtasks
        )
    }

    // --- теги --------------------------------------------------------------

    private fun parseTag(token: String): String? {
        if (!token.startsWith('#') || token.length < 2) return null
        val name = token.drop(1).trimEnd(*TRAILING_PUNCTUATION)
        return name.ifEmpty { null }
    }

    // --- приоритет ---------------------------------------------------------

    private fun parsePriority(tokens: List<String>, index: Int): Match<Priority>? {
        val token = tokens[index].lowercase().trimEnd(*TRAILING_PUNCTUATION)

        PRIORITY_REGEX.matchEntire(token)?.let { m ->
            val p = when (m.groupValues[1]) {
                "1" -> Priority.P1
                "2" -> Priority.P2
                "3" -> Priority.P3
                else -> Priority.P4
            }
            return Match(p, 1)
        }

        if (token in P1_WORDS) return Match(Priority.P1, 1)
        if (token in P2_WORDS) return Match(Priority.P2, 1)

        val twoWords = if (index + 1 < tokens.size) "$token ${tokens[index + 1].lowercase().trimEnd(*TRAILING_PUNCTUATION)}" else null
        if (twoWords != null) {
            when (twoWords) {
                "очень важно", "высший приоритет", "первый приоритет" -> return Match(Priority.P1, 2)
                "высокий приоритет", "второй приоритет" -> return Match(Priority.P2, 2)
                "средний приоритет", "третий приоритет" -> return Match(Priority.P3, 2)
                "не срочно", "низкий приоритет", "не к спеху" -> return Match(Priority.P4, 2)
            }
        }

        return null
    }

    // --- сложность ---------------------------------------------------------

    private fun parseDifficulty(tokens: List<String>, index: Int): Match<Int>? {
        val token = tokens[index].lowercase().trimEnd(*TRAILING_PUNCTUATION)

        DIFFICULTY_REGEX.matchEntire(token)?.let { m ->
            val level = m.groupValues[1].toIntOrNull()?.coerceIn(1, 5) ?: return null
            return Match(level, 1)
        }

        val nextToken = tokens.getOrNull(index + 1)?.lowercase()?.trimEnd(*TRAILING_PUNCTUATION)
        if (token == "сложность" || token == "diff") {
            nextToken?.toIntOrNull()?.let { n ->
                if (n in 1..5) return Match(n, 2)
            }
        }

        val twoWords = if (nextToken != null) "$token $nextToken" else null
        if (twoWords != null) {
            when (twoWords) {
                "очень легко", "очень просто", "на изи" -> return Match(1, 2)
                "очень сложно", "очень сложная", "пипец сложно" -> return Match(5, 2)
                "средняя сложность" -> return Match(3, 2)
            }
        }

        return when (token) {
            "изи" -> Match(1, 1)
            "легко", "просто", "легкая", "простая" -> Match(2, 1)
            "средне", "обычная" -> Match(3, 1)
            "сложно", "сложная", "тяжело", "трудно", "трудная" -> Match(4, 1)
            "хард", "жесть" -> Match(5, 1)
            else -> null
        }
    }

    // --- оценка времени ----------------------------------------------------

    private fun parseEstimate(tokens: List<String>, index: Int): Match<Int>? {
        val token = tokens[index].lowercase().trimEnd(*TRAILING_PUNCTUATION)

        ESTIMATE_REGEX.matchEntire(token)?.let { m ->
            val amount = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val unit = m.groupValues[2]
            val minutes = when {
                unit.startsWith("ч") || unit.startsWith("h") -> amount * 60
                else -> amount
            }
            val rounded = Math.round(minutes).toInt().coerceIn(1, 24 * 60)
            return Match(rounded, 1)
        }

        // «на полчаса», «на час», «на пару часов»
        if (token == "на" && index + 1 < tokens.size) {
            val next = tokens[index + 1].lowercase().trimEnd(*TRAILING_PUNCTUATION)
            when (next) {
                "полчаса" -> return Match(30, 2)
                "час" -> return Match(60, 2)
                "пару" -> {
                    val afterNext = tokens.getOrNull(index + 2)?.lowercase()?.trimEnd(*TRAILING_PUNCTUATION)
                    if (afterNext != null && afterNext.startsWith("час")) return Match(120, 3)
                }
            }
            // «на 30 минут», «на 2 часа»
            next.toDoubleOrNull()?.let { num ->
                val unit = tokens.getOrNull(index + 2)?.lowercase()?.trimEnd(*TRAILING_PUNCTUATION).orEmpty()
                val minutes = when {
                    unit.startsWith("ч") || unit.startsWith("час") -> num * 60
                    unit.startsWith("м") || unit.startsWith("мин") -> num
                    else -> null
                }
                if (minutes != null) {
                    return Match(Math.round(minutes).toInt().coerceIn(1, 24 * 60), 3)
                }
            }
        }

        // «займет 30 минут», «около часа», «примерно 45 минут»
        if (token in ESTIMATE_PREFIXES && index + 1 < tokens.size) {
            val next = tokens[index + 1].lowercase().trimEnd(*TRAILING_PUNCTUATION)
            if (next == "час") return Match(60, 2)
            if (next == "полчаса") return Match(30, 2)
            next.toDoubleOrNull()?.let { num ->
                val unit = tokens.getOrNull(index + 2)?.lowercase()?.trimEnd(*TRAILING_PUNCTUATION).orEmpty()
                val minutes = when {
                    unit.startsWith("ч") || unit.startsWith("час") -> num * 60
                    unit.startsWith("м") || unit.startsWith("мин") -> num
                    else -> null
                }
                if (minutes != null) {
                    return Match(Math.round(minutes).toInt().coerceIn(1, 24 * 60), 3)
                }
            }
        }

        return null
    }

    // --- время -------------------------------------------------------------

    private data class Match<T>(val value: T, val consumed: Int)

    private fun parseTime(tokens: List<String>, index: Int, currentTime: LocalTime): Match<LocalTime>? {
        var offset = 0
        val raw = tokens[index].lowercase().trimEnd(*TRAILING_PUNCTUATION)

        // «через час», «через полчаса», «через N минут», «через N часов»
        if (raw == "через" && index + 1 < tokens.size) {
            val next = tokens[index + 1].lowercase().trimEnd(*TRAILING_PUNCTUATION)
            when (next) {
                "полчаса" -> return Match(currentTime.plusMinutes(30), 2)
                "час" -> return Match(currentTime.plusHours(1), 2)
            }
            next.toIntOrNull()?.let { n ->
                val unit = tokens.getOrNull(index + 2)?.lowercase()?.trimEnd(*TRAILING_PUNCTUATION).orEmpty()
                return when {
                    unit.startsWith("мин") || unit == "м" -> Match(currentTime.plusMinutes(n.toLong()), 3)
                    unit.startsWith("час") || unit == "ч" -> Match(currentTime.plusHours(n.toLong()), 3)
                    else -> null
                }
            }
        }

        // предлог: «в 5 вечера», «к 18:00»
        val hasPreposition = raw in TIME_PREPOSITIONS
        if (hasPreposition && index + 1 < tokens.size) offset = 1
        val token = tokens.getOrNull(index + offset)?.lowercase()?.trimEnd(*TRAILING_PUNCTUATION) ?: return null

        // Части суток с предлогом: «в полдень», «в полночь», «в обед»
        if (hasPreposition) {
            when (token) {
                "обед" -> return Match(LocalTime.of(13, 0), offset + 1)
                "полдень" -> return Match(LocalTime.of(12, 0), offset + 1)
                "полночь" -> return Match(LocalTime.of(0, 0), offset + 1)
            }
        }
        // Наречные части суток: «утром», «вечером», «днём»
        DAY_PARTS[token]?.let { return Match(it, offset + 1) }

        // Проверка 12-часовых модификаторов на русском: «вечера», «дня», «утра», «ночи»
        // Например: «в 5 вечера», «в 5 часов вечера», «в пять вечера»
        var hourVal = token.toIntOrNull() ?: TEXT_NUMBERS[token]
        var extraTokens = 0

        if (hourVal != null && hourVal in 1..12) {
            var modifierToken = tokens.getOrNull(index + offset + 1)?.lowercase()?.trimEnd(*TRAILING_PUNCTUATION)
            if (modifierToken == "часов" || modifierToken == "часа" || modifierToken == "час") {
                extraTokens++
                modifierToken = tokens.getOrNull(index + offset + 2)?.lowercase()?.trimEnd(*TRAILING_PUNCTUATION)
            }

            if (modifierToken != null) {
                when {
                    modifierToken.startsWith("вечер") -> {
                        val h = if (hourVal < 12) hourVal + 12 else 0
                        return Match(LocalTime.of(h, 0), offset + 2 + extraTokens)
                    }
                    modifierToken.startsWith("дня") || modifierToken == "день" -> {
                        val h = if (hourVal in 1..6) hourVal + 12 else hourVal
                        return Match(LocalTime.of(h, 0), offset + 2 + extraTokens)
                    }
                    modifierToken.startsWith("утр") -> {
                        val h = if (hourVal == 12) 0 else hourVal
                        return Match(LocalTime.of(h, 0), offset + 2 + extraTokens)
                    }
                    modifierToken.startsWith("ноч") -> {
                        val h = if (hourVal in 1..5) hourVal else 23
                        return Match(LocalTime.of(h, 0), offset + 2 + extraTokens)
                    }
                }
            }
        }

        // Стандартный HH:MM («18:00», «18:00 вечера»)
        HHMM_REGEX.matchEntire(token)?.let { m ->
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            if (h > 23 || min > 59) return null

            val modifierToken = tokens.getOrNull(index + offset + 1)?.lowercase()?.trimEnd(*TRAILING_PUNCTUATION)
            if (modifierToken != null && h in 1..12) {
                if (modifierToken == "pm") {
                    val finalH = if (h < 12) h + 12 else h
                    return Match(LocalTime.of(finalH, min), offset + 2)
                }
                if (modifierToken == "am") {
                    val finalH = if (h == 12) 0 else h
                    return Match(LocalTime.of(finalH, min), offset + 2)
                }
                if (modifierToken.startsWith("вечер") && h < 12) {
                    return Match(LocalTime.of(h + 12, min), offset + 2)
                }
                if ((modifierToken.startsWith("дня") || modifierToken == "день") && h in 1..6) {
                    return Match(LocalTime.of(h + 12, min), offset + 2)
                }
                if (modifierToken.startsWith("утр") && h == 12) {
                    return Match(LocalTime.of(0, min), offset + 2)
                }
                if (modifierToken.startsWith("ноч") && h in 11..12) {
                    return Match(LocalTime.of(if (h == 12) 0 else 23, min), offset + 2)
                }
            }
            return Match(LocalTime.of(h, min), offset + 1)
        }

        // 12-часовой формат English: 6pm, 6:30am, 10pm
        TIME_12H_REGEX.matchEntire(token)?.let { m ->
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            val ampm = m.groupValues[3].lowercase()
            if (h in 1..12 && min in 0..59) {
                if (ampm == "pm" && h < 12) h += 12
                if (ampm == "am" && h == 12) h = 0
                return Match(LocalTime.of(h, min), offset + 1)
            }
        }

        // «6 pm», «6:30 am» с пробелом
        val nextToken = tokens.getOrNull(index + offset + 1)?.lowercase()?.trimEnd(*TRAILING_PUNCTUATION)
        if (nextToken == "am" || nextToken == "pm") {
            token.toIntOrNull()?.let { hRaw ->
                if (hRaw in 1..12) {
                    var h = hRaw
                    if (nextToken == "pm" && h < 12) h += 12
                    if (nextToken == "am" && h == 12) h = 0
                    return Match(LocalTime.of(h, 0), offset + 2)
                }
            }
        }

        // «в 9», «в 18» — только с предлогом
        if (hasPreposition) {
            hourVal?.let { h ->
                if (h in 0..23) return Match(LocalTime.of(h, 0), offset + 1)
            }
        }

        return null
    }

    // --- дата --------------------------------------------------------------

    private fun parseDate(tokens: List<String>, index: Int, today: LocalDate): Match<LocalDate>? {
        var offset = 0
        val raw = tokens[index].lowercase().trimEnd(*TRAILING_PUNCTUATION)
        val normalized = normalizeDateTypo(raw)

        // «на выходных», «в выходные» -> ближайшая суббота
        if ((normalized == "на" || normalized == "в" || normalized == "во") && index + 1 < tokens.size) {
            val next = normalizeDateTypo(tokens[index + 1].lowercase().trimEnd(*TRAILING_PUNCTUATION))
            if (next.startsWith("выходн")) {
                return Match(nextWeekday(today, DayOfWeek.SATURDAY), 2)
            }
            if (next == "конце" && index + 2 < tokens.size && tokens[index + 2].lowercase().startsWith("недел")) {
                return Match(nextWeekday(today, DayOfWeek.FRIDAY), 3)
            }
        }

        // «через N дней», «через неделю», «через месяц»
        if (normalized == "через") {
            val next = tokens.getOrNull(index + 1)?.lowercase()?.trimEnd(*TRAILING_PUNCTUATION) ?: return null
            next.toIntOrNull()?.let { n ->
                val unit = tokens.getOrNull(index + 2)?.lowercase().orEmpty()
                return when {
                    unit.startsWith("дн") || unit.startsWith("ден") -> Match(today.plusDays(n.toLong()), 3)
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

        // Предлог перед днём недели: «в этот понедельник», «в среду»
        val word: String
        if (normalized in DATE_PREPOSITIONS && index + 1 < tokens.size) {
            offset = 1
            val nxt = normalizeDateTypo(tokens[index + 1].lowercase().trimEnd(*TRAILING_PUNCTUATION))
            if ((nxt == "этот" || nxt == "эту" || nxt == "следующий" || nxt == "следующую") && index + 2 < tokens.size) {
                offset = 2
                word = normalizeDateTypo(tokens[index + 2].lowercase().trimEnd(*TRAILING_PUNCTUATION))
            } else {
                word = nxt
            }
        } else {
            word = normalized
        }

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
            val result = if (yearPart.isEmpty() && candidate < today) candidate.plusYears(1) else candidate
            return Match(result, offset + 1)
        }

        return null
    }

    private fun normalizeDateTypo(word: String): String = when (word) {
        "завттра", "завтро", "завтраа" -> "завтра"
        "седня", "севодня", "сегодняя" -> "сегодня"
        "послезавттра", "послезавтро" -> "послезавтра"
        "понедельнек" -> "понедельник"
        "вторнек" -> "вторник"
        "пятнеца" -> "пятница"
        "субота" -> "суббота"
        else -> word
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun nextWeekday(today: LocalDate, target: DayOfWeek): LocalDate {
        var d = today.plusDays(1)
        while (d.dayOfWeek != target) d = d.plusDays(1)
        return d
    }

    private fun weekdayOf(word: String): DayOfWeek? = WEEKDAYS[word]

    private fun monthOf(word: String): Int? = MONTHS[word.trimEnd(*TRAILING_PUNCTUATION)]

    // --- семантический подбор эмодзи ---------------------------------------

    fun detectEmoji(title: String): String? {
        val lower = title.lowercase()
        for ((root, emoji) in EMOJI_ROOTS) {
            if (lower.contains(root)) return emoji
        }
        return null
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
        val TRAILING_PUNCTUATION = charArrayOf(',', '.', ';', ':', '!', '?')

        val PRIORITY_REGEX = Regex("^!p?([1-4])$")
        val DIFFICULTY_REGEX = Regex("^\\*([1-5])$")
        val ESTIMATE_REGEX = Regex("^~(\\d+(?:[.,]\\d+)?)\\s*(мин|м|ч|h|m)?$")
        val HHMM_REGEX = Regex("^(\\d{1,2}):(\\d{2})$")
        val TIME_12H_REGEX = Regex("^(\\d{1,2})(?::(\\d{2}))?(am|pm)$", RegexOption.IGNORE_CASE)
        val NUMERIC_DATE_REGEX = Regex("^(\\d{1,2})[./](\\d{1,2})(?:[./](\\d{2}|\\d{4}))?$")

        val TIME_PREPOSITIONS = setOf("в", "во", "к")
        val DATE_PREPOSITIONS = setOf("в", "во", "до", "на")
        val ESTIMATE_PREFIXES = setOf("займет", "займёт", "около", "примерно")
        val CLAUSE_CONJUNCTIONS = setOf("когда", "если", "чтобы", "потому", "так как", "где", "куда", "что", "но", "а")

        val TEXT_NUMBERS = mapOf(
            "один" to 1, "одна" to 1, "час" to 1,
            "два" to 2, "две" to 2,
            "три" to 3,
            "четыре" to 4,
            "пять" to 5,
            "шесть" to 6,
            "семь" to 7,
            "восемь" to 8,
            "девять" to 9,
            "десять" to 10,
            "одиннадцать" to 11,
            "двенадцать" to 12
        )

        val P1_WORDS = setOf("срочно", "критично", "горит", "аврал", "asap", "немедленно", "!срочно")
        val P2_WORDS = setOf("важно", "важная", "!важно")

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

        val EMOJI_ROOTS: List<Pair<String, String>> = listOf(
            "магазин" to "🛒",
            "покуп" to "🛒",
            "купить" to "🛒",
            "супермаркет" to "🛒",
            "молок" to "🥛",
            "сливк" to "🥛",
            "кефир" to "🥛",
            "творог" to "🥛",
            "сыр" to "🧀",
            "спарж" to "🥦",
            "овощ" to "🥗",
            "фрукт" to "🍎",
            "яблок" to "🍎",
            "хлеб" to "🥖",
            "пицц" to "🍕",
            "бургер" to "🍔",
            "суши" to "🍣",
            "кофе" to "☕",
            "чай" to "🍵",
            "торт" to "🍰",
            "десерт" to "🍰",
            "обед" to "🍽️",
            "ужин" to "🍽️",
            "завтрак" to "🍳",
            "готов" to "🍳",
            "позвон" to "📞",
            "звон" to "📞",
            "набрат" to "📞",
            "созвон" to "📞",
            "написа" to "✉️",
            "письм" to "✉️",
            "email" to "✉️",
            "сообщен" to "💬",
            "встреч" to "👥",
            "митинг" to "👥",
            "zoom" to "👥",
            "тренировк" to "🏋️",
            "зал" to "🏋️",
            "спорт" to "🏋️",
            "фитнес" to "🏋️",
            "бег" to "🏃",
            "пробежк" to "🏃",
            "гулят" to "🚶",
            "прогулк" to "🚶",
            "бассейн" to "🏊",
            "плава" to "🏊",
            "йог" to "🧘",
            "велик" to "🚴",
            "велосипед" to "🚴",
            "врач" to "🩺",
            "доктор" to "🩺",
            "стоматолог" to "🦷",
            "зуб" to "🦷",
            "поликлиник" to "🩺",
            "больниц" to "🩺",
            "таблетк" to "💊",
            "витамин" to "💊",
            "лекарств" to "💊",
            "аптек" to "💊",
            "уборк" to "🧹",
            "убрать" to "🧹",
            "пылесос" to "🧹",
            "пыль" to "🧹",
            "стирк" to "🧺",
            "мусор" to "🗑️",
            "машин" to "🚗",
            "авто" to "🚗",
            "заправк" to "⛽",
            "бензин" to "⛽",
            "мойк" to "🚗",
            "билет" to "✈️",
            "самолет" to "✈️",
            "поезд" to "🚆",
            "оплат" to "💳",
            "счет" to "💳",
            "жкх" to "💳",
            "налог" to "💳",
            "банк" to "🏦",
            "деньг" to "💰",
            "подарок" to "🎁",
            "цвет" to "🪴",
            "полити" to "🪴",
            "собак" to "🐾",
            "кот" to "🐾",
            "фильм" to "🎬",
            "кино" to "🎬",
            "сериал" to "🎬",
            "музык" to "🎧",
            "игр" to "🎮",
            "книг" to "📚",
            "учеб" to "🎓",
            "экзамен" to "🎓",
            "код" to "💻",
            "программ" to "💻",
            "баг" to "💻",
            "коммит" to "💻",
            "договор" to "📄",
            "документ" to "📄",
            "стрижк" to "✂️",
            "душ" to "🚿",
            "сон" to "😴",
            "ремонт" to "🔧"
        )
    }
}
