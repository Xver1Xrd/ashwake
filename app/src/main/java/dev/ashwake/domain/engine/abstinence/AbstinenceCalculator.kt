package dev.ashwake.domain.engine.abstinence

import dev.ashwake.domain.model.abstinence.Abstinence
import dev.ashwake.domain.model.abstinence.Attempt
import dev.ashwake.domain.model.abstinence.Milestone
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

data class Savings(
    val units: Float,
    val money: Float,
    val unitName: String,
    val currency: String
)

data class AbstinenceStats(
    /** Момент, от которого тикает счётчик: старт попытки плюс штрафные дни. */
    val effectiveStart: Instant,
    val current: Duration,
    val record: Duration,
    val totalCleanDays: Long,
    val attemptNumber: Int,
    val nextMilestone: Milestone?,
    val progressToNextMilestone: Float,
    val savings: Savings?
) {
    val currentDays: Long get() = current.toDays()
}

/**
 * Расчёты экрана отказа (п. 4).
 *
 * Ничего из этого в базе не хранится: рекорд, сумма чистых дней и экономия
 * выводятся из истории попыток. Хранить их пришлось бы пересчитывать при любой
 * правке истории — например, при отмене срыва в течение суток.
 */
@Singleton
class AbstinenceCalculator @Inject constructor() {

    fun stats(abstinence: Abstinence, now: Instant): AbstinenceStats {
        val attempts = abstinence.attempts.sortedBy { it.startedAt }
        val current = attempts.lastOrNull { it.isCurrent }

        val effectiveStart = current?.let { effectiveStartOf(it) } ?: now
        val currentDuration = if (current != null) {
            Duration.between(effectiveStart, now).coerceAtLeast(Duration.ZERO)
        } else Duration.ZERO

        val record = attempts.maxOfOrNull { durationOf(it, now) } ?: Duration.ZERO
        val totalCleanDays = attempts.sumOf { durationOf(it, now).toDays() }

        val nextMilestone = nextMilestone(abstinence, currentDuration.toDays())
        val previousDays = previousMilestoneDays(abstinence, currentDuration.toDays())

        return AbstinenceStats(
            effectiveStart = effectiveStart,
            current = currentDuration,
            record = record,
            totalCleanDays = totalCleanDays,
            attemptNumber = current?.ordinal ?: attempts.size,
            nextMilestone = nextMilestone,
            progressToNextMilestone = progress(currentDuration.toDays(), previousDays, nextMilestone),
            savings = savings(abstinence, currentDuration)
        )
    }

    /**
     * Штрафные дни режима GENTLE отодвигают старт вперёд.
     * Счётчик из-за этого может уйти в ноль, но не в минус: «минус три дня
     * трезвости» — бессмысленная величина.
     */
    fun effectiveStartOf(attempt: Attempt): Instant =
        attempt.startedAt.plus(Duration.ofDays(attempt.penaltyDays.toLong()))

    fun durationOf(attempt: Attempt, now: Instant): Duration {
        val end = attempt.endedAt ?: now
        return Duration.between(effectiveStartOf(attempt), end).coerceAtLeast(Duration.ZERO)
    }

    /** Ближайшая недостигнутая веха. */
    fun nextMilestone(abstinence: Abstinence, currentDays: Long): Milestone? {
        if (!abstinence.milestonesEnabled) return null
        return milestonesOf(abstinence)
            .filter { it.days > currentDays }
            .minByOrNull { it.days }
    }

    /** Все вехи: пользовательские плюс стандартные, без дублей по числу дней. */
    fun milestonesOf(abstinence: Abstinence): List<Milestone> {
        val custom = abstinence.milestones
        val customDays = custom.map { it.days }.toSet()
        val defaults = defaultMilestoneDays(customDays.maxOrNull() ?: 0)
            .filterNot { it in customDays }
            .map { days ->
                Milestone(abstinenceId = abstinence.id, days = days, title = titleOf(days))
            }
        return (custom + defaults).sortedBy { it.days }
    }

    /**
     * Доля пути от предыдущей вехи к следующей.
     * Считать от нуля нельзя: между 365 и 730 днями кольцо стояло бы почти полным
     * весь год и не показывало бы движения.
     */
    private fun progress(currentDays: Long, previousDays: Long, next: Milestone?): Float {
        if (next == null) return 1f
        val span = (next.days - previousDays).coerceAtLeast(1)
        return ((currentDays - previousDays).toFloat() / span).coerceIn(0f, 1f)
    }

    private fun previousMilestoneDays(abstinence: Abstinence, currentDays: Long): Long =
        milestonesOf(abstinence)
            .filter { it.days <= currentDays }
            .maxOfOrNull { it.days.toLong() } ?: 0L

    /** Экономия считается только из baseline. Нет baseline — нет и блока. */
    fun savings(abstinence: Abstinence, duration: Duration): Savings? {
        val baseline = abstinence.baseline ?: return null
        // Дробная часть суток учитывается: за полдня без сигарет сэкономлена половина пачки
        val days = duration.toMillis() / MILLIS_PER_DAY.toFloat()
        val units = baseline.unitsPerDay * days
        return Savings(
            units = units,
            money = units * baseline.costPerUnit,
            unitName = baseline.unitName,
            currency = baseline.currency
        )
    }

    /**
     * Вехи, которые надо отметить достигнутыми при пересечении.
     * Возвращаются только те, что ещё не отмечены — воркер вызывает это
     * периодически, и повторных уведомлений быть не должно.
     */
    fun newlyReached(abstinence: Abstinence, now: Instant): List<Milestone> {
        val current = abstinence.currentAttempt ?: return emptyList()
        val days = Duration.between(effectiveStartOf(current), now).toDays()
        return abstinence.milestones.filter { it.days <= days && it.reachedAt == null }
    }

    /** 1, 3, 7, 14, 30, 60, 90, 180, 270, 365, дальше каждый год (п. 4). */
    fun defaultMilestoneDays(atLeastUpTo: Int = 0): List<Int> {
        val base = listOf(1, 3, 7, 14, 30, 60, 90, 180, 270, 365)
        val years = generateSequence(2) { it + 1 }
            .map { it * 365 }
            .takeWhile { it <= maxOf(atLeastUpTo, YEARS_AHEAD * 365) }
            .toList()
        return base + years
    }

    fun titleOf(days: Int): String = when {
        days == 1 -> "1 день"
        days < 30 -> "$days ${pluralDays(days)}"
        days % 365 == 0 -> "${days / 365} ${pluralYears(days / 365)}"
        else -> "$days ${pluralDays(days)}"
    }

    private fun pluralDays(days: Int): String {
        val mod100 = days % 100
        val mod10 = days % 10
        return when {
            mod100 in 11..14 -> "дней"
            mod10 == 1 -> "день"
            mod10 in 2..4 -> "дня"
            else -> "дней"
        }
    }

    private fun pluralYears(years: Int): String = when {
        years % 100 in 11..14 -> "лет"
        years % 10 == 1 -> "год"
        years % 10 in 2..4 -> "года"
        else -> "лет"
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        /** На сколько лет вперёд заготавливать годовые вехи. */
        const val YEARS_AHEAD = 5
    }
}
