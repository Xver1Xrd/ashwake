package dev.ashwake.domain.engine.tasks

import dev.ashwake.domain.model.tasks.RecurrenceRule
import dev.ashwake.domain.model.tasks.RecurrenceType
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Расчёт следующего срока для повторяющейся задачи.
 * Чистый класс без зависимостей от Android — проверяется юнит-тестами.
 */
@Singleton
class RecurrenceCalculator @Inject constructor() {

    /**
     * Следующая дата строго после [after].
     * @param after точка отсчёта: плановая дата или дата факта, если [RecurrenceRule.fromCompletion].
     * @return null, если правило истекло (сработал endDate).
     */
    fun next(rule: RecurrenceRule, after: LocalDate): LocalDate? {
        val candidate = when (rule.type) {
            RecurrenceType.DAILY -> after.plusDays(1)

            RecurrenceType.EVERY_N_DAYS -> {
                val step = (rule.intervalDays ?: 1).coerceAtLeast(1).toLong()
                if (rule.fromCompletion) {
                    after.plusDays(step)
                } else {
                    // Держимся исходной сетки: пропущенные повторы не сдвигают серию.
                    val elapsed = ChronoUnit.DAYS.between(rule.startDate, after)
                    val periods = Math.floorDiv(elapsed, step) + 1
                    rule.startDate.plusDays(periods * step)
                }
            }

            RecurrenceType.WEEKDAYS -> {
                if (rule.weekdays.isEmpty) return null
                var d = after.plusDays(1)
                var guard = 0
                while (d.dayOfWeek !in rule.weekdays) {
                    d = d.plusDays(1)
                    if (++guard > 7) return null
                }
                d
            }

            RecurrenceType.DAY_OF_MONTH -> {
                val target = rule.dayOfMonth ?: return null
                var month = after.withDayOfMonth(1)
                var result: LocalDate? = null
                var guard = 0
                // 31-е число: месяцы короче пропускаем, а не съезжаем на 28-е —
                // «каждое 31-е» не должно превращаться в «каждое последнее».
                while (result == null && guard < 24) {
                    val candidateInMonth =
                        if (target <= month.lengthOfMonth()) month.withDayOfMonth(target) else null
                    if (candidateInMonth != null && candidateInMonth > after) {
                        result = candidateInMonth
                    }
                    month = month.plusMonths(1)
                    guard++
                }
                result ?: return null
            }
        }

        if (rule.endDate != null && candidate > rule.endDate) return null
        return candidate
    }

    /** Все повторы в интервале — нужно календарю и планировщику дня. */
    fun occurrencesBetween(
        rule: RecurrenceRule,
        from: LocalDate,
        to: LocalDate,
        limit: Int = 366
    ): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var cursor = from.minusDays(1)
        while (result.size < limit) {
            val nextDate = next(rule, cursor) ?: break
            if (nextDate > to) break
            if (nextDate >= from) result += nextDate
            cursor = nextDate
        }
        return result
    }
}
