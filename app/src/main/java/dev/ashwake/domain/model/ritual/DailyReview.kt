package dev.ashwake.domain.model.ritual

import java.time.Instant
import java.time.LocalDate

/** Когда ритуал был пройден: вечером своего дня или утром за вчера (п. 9). */
enum class ReviewCompletion { EVENING, NEXT_MORNING }

/**
 * Итог вечернего ритуала.
 *
 * Настроение и энергия — отдельные шкалы, а не одна «оценка дня»: именно их
 * пары с привычками ищет [dev.ashwake.domain.engine.analytics.CorrelationAnalyzer],
 * и слитая воедино оценка эти связи бы стёрла.
 */
data class DailyReview(
    val date: LocalDate,
    val dayRating: Int? = null,
    val mood: Int? = null,
    val energy: Int? = null,
    val note: String? = null,
    val completedAt: Instant = Instant.EPOCH,
    val completedAs: ReviewCompletion = ReviewCompletion.EVENING,
    /** Три главные задачи на завтра. */
    val topTaskIds: List<Long> = emptyList()
) {
    val hasScales: Boolean get() = mood != null && energy != null
}
